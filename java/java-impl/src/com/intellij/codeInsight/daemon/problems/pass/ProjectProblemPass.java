// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.problems.ChangeSet;
import com.intellij.codeInsight.daemon.problems.ProblemCollector;
import com.intellij.codeInsight.daemon.problems.ScopedMember;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.daemon.problems.SnapshotUpdater.collectChanges;
import static com.intellij.codeInsight.daemon.problems.SnapshotUpdater.updateSnapshot;
import static com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils.*;

public class ProjectProblemPass extends TextEditorHighlightingPass {

  private final Editor myEditor;
  private final PsiJavaFile myFile;

  private final SmartPointerManager myPointerManager = SmartPointerManager.getInstance(myProject);

  private Map<Change, List<SmartPsiElementPointer<PsiElement>>> myProblems = null;
  private Map<SmartPsiElementPointer<PsiMember>, ScopedMember> mySnapshot = null;

  ProjectProblemPass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiJavaFile file) {
    super(project, editor.getDocument());
    myEditor = editor;
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    ChangeSet changeSet = collectChanges(myFile);
    if (changeSet == null) return;
    Map<PsiMember, ScopedMember> changes = mergeWithOldChanges(myFile, changeSet.getChanges());
    myProblems = ReadAction.nonBlocking(() -> collectProblems(changes)).executeSynchronously();
    mySnapshot = changeSet.getNewSnapshot();
  }

  private @Nullable Map<Change, List<SmartPsiElementPointer<PsiElement>>> collectProblems(@NotNull Map<PsiMember, ScopedMember> changes) {
    if (changes.isEmpty()) return Collections.emptyMap();
    Map<Change, List<SmartPsiElementPointer<PsiElement>>> problems = new HashMap<>();
    for (Map.Entry<PsiMember, ScopedMember> entry : changes.entrySet()) {
      if (myProject.isDisposed()) return null;
      PsiMember curMember = entry.getKey();
      ScopedMember prevMember = entry.getValue();
      Set<PsiElement> memberProblems = ProblemCollector.collect(prevMember, curMember);
      if (memberProblems == null) memberProblems = Collections.emptySet();
      SmartPsiElementPointer<PsiMember> curMemberPointer = myPointerManager.createSmartPsiElementPointer(curMember);
      Change change = new Change(curMemberPointer, prevMember);
      problems.put(change, ContainerUtil.map(memberProblems, p -> myPointerManager.createSmartPsiElementPointer(p)));
    }
    return problems;
  }

  @Override
  public void doApplyInformationToEditor() {
    Document document = myDocument;
    if (document == null) return;
    Map<SmartPsiElementPointer<PsiMember>, ScopedMember> snapshot = mySnapshot;
    if (snapshot == null) return;
    Map<Change, List<SmartPsiElementPointer<PsiElement>>> problems = myProblems;
    if (problems == null) return;
    InlayModel inlayModel = myEditor.getInlayModel();
    PresentationFactory factory = new PresentationFactory((EditorImpl)myEditor);
    Map<SmartPsiElementPointer<PsiMember>, ReportedChange> reportedChanges = new HashMap<>();
    problems.forEach((change, changeProblems) -> {
      ScopedMember prevMember = change.prevMember;
      SmartPsiElementPointer<PsiMember> memberPointer = change.curMemberPointer;
      PsiMember member = memberPointer.getElement();
      if (member == null) return;
      reportedChanges.computeIfAbsent(memberPointer, (k) -> {
        if (changeProblems.isEmpty()) return new ReportedChange(prevMember, null);
        int offset = getMemberOffset(member);
        InlayPresentation presentation = getPresentation(myProject, myEditor, document, factory, offset, member, changeProblems);
        BlockInlayRenderer renderer = createBlockRenderer(presentation);
        Inlay<?> newInlay = inlayModel.addBlockElement(offset, true, true, BlockInlayPriority.PROBLEMS, renderer);
        return new ReportedChange(prevMember, newInlay);
      });
    });
    reportChanges(myFile, reportedChanges);
    updateSnapshot(myFile, snapshot);
  }

  private static class Change {

    private final SmartPsiElementPointer<PsiMember> curMemberPointer;
    private final ScopedMember prevMember;

    private Change(SmartPsiElementPointer<PsiMember> curMemberPointer, ScopedMember prevMember) {
      this.curMemberPointer = curMemberPointer;
      this.prevMember = prevMember;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Change change = (Change)o;
      return Objects.equals(curMemberPointer, change.curMemberPointer) &&
             Objects.equals(prevMember, change.prevMember);
    }

    @Override
    public int hashCode() {
      return Objects.hash(curMemberPointer, prevMember);
    }

    @Override
    public String toString() {
      return "Change{" +
             "curMemberPointer=" + curMemberPointer +
             ", prevMember=" + prevMember +
             '}';
    }
  }
}
