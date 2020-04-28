// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeInsight.daemon.problems.FileState;
import com.intellij.codeInsight.daemon.problems.FileStateUpdater;
import com.intellij.codeInsight.daemon.problems.ProblemCollector;
import com.intellij.codeInsight.daemon.problems.ScopedMember;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInspection.SmartHashMap;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils.*;

public class ProjectProblemPass extends EditorBoundHighlightingPass {

  private final FileEditorManager myEditorManager = FileEditorManager.getInstance(myProject);

  private Map<PsiMember, Problem> myProblems = null;
  private Map<SmartPsiElementPointer<PsiMember>, ScopedMember> mySnapshot = null;

  ProjectProblemPass(@NotNull Editor editor, @NotNull PsiJavaFile file) {
    super(editor, file, true);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    FileState prevState = FileStateUpdater.getState(myFile);
    if (prevState == null) return;
    FileState curState = FileStateUpdater.findState(myFile, prevState.getSnapshot());
    myProblems = collectProblems(curState.getChanges(), prevState.getChanges());
    mySnapshot = curState.getSnapshot();
  }

  @Override
  public void doApplyInformationToEditor() {
    Map<SmartPsiElementPointer<PsiMember>, ScopedMember> snapshot = mySnapshot;
    if (snapshot == null) return;
    Map<PsiMember, Problem> problems = myProblems;
    if (problems == null) return;

    PresentationFactory factory = new PresentationFactory((EditorImpl)myEditor);
    Map<PsiMember, Inlay<?>> inlays = getInlays(myEditor);
    Map<PsiMember, ScopedMember> changes = new SmartHashMap<>();
    problems.forEach((curMember, problem) -> {
      ScopedMember prevMember = problem.prevMember;
      Set<PsiElement> brokenUsages = problem.brokenUsages;
      changes.put(curMember, prevMember);
      if (brokenUsages != null) addInlay(factory, curMember, brokenUsages, inlays);
    });
    updateInlays(myEditor, inlays);

    FileState fileState = new FileState(snapshot, changes);
    FileStateUpdater.updateState(myFile, fileState);
    updateTimestamp(myEditor);
  }

  private void addInlay(PresentationFactory factory,
                        PsiMember psiMember,
                        @NotNull Set<PsiElement> brokenUsages,
                        @NotNull Map<PsiMember, Inlay<?>> inlays) {
    Inlay<?> oldInlay = inlays.remove(psiMember);
    if (oldInlay != null) Disposer.dispose(oldInlay);
    if (brokenUsages.isEmpty() || hasOtherElementsOnSameLine(psiMember)) return;
    int offset = getMemberOffset(psiMember);
    InlayPresentation presentation = getPresentation(myProject, myEditor, myEditor.getDocument(), factory, offset, psiMember, brokenUsages);
    BlockInlayRenderer renderer = createBlockRenderer(presentation);
    Inlay<?> newInlay = myEditor.getInlayModel().addBlockElement(offset, true, true, BlockInlayPriority.PROBLEMS, renderer);
    addListener(renderer, newInlay);
    inlays.put(psiMember, newInlay);
  }

  private @NotNull Map<PsiMember, Problem> collectProblems(@NotNull Map<PsiMember, ScopedMember> curChanges,
                                                           @NotNull Map<PsiMember, ScopedMember> oldChanges) {
    if (inSplitEditorMode() && curChanges.isEmpty() && !isDocumentUpdated(myEditor)) {
      // some other file changed. this change might be a new broken usage of one of members in current file.
      // so, now we need to recheck all of the previous changes.
      curChanges = oldChanges;
      oldChanges = Collections.emptyMap();
    }
    Map<PsiMember, Problem> problems = ContainerUtil.map2Map(oldChanges.entrySet(),
                                                             e -> Pair.create(e.getKey(), new Problem(e.getValue(), null)));
    curChanges.forEach((curMember, prevMember) -> {
      Set<PsiElement> changeProblems = ProblemCollector.collect(prevMember, curMember);
      if (changeProblems == null) changeProblems = Collections.emptySet();
      problems.put(curMember, new Problem(prevMember, changeProblems));
    });
    return problems;
  }

  private boolean inSplitEditorMode() {
    return myEditorManager.getSelectedEditors().length > 1;
  }

  private static class Problem {
    private final ScopedMember prevMember;
    private final Set<PsiElement> brokenUsages;

    private Problem(ScopedMember prevMember, Set<PsiElement> brokenUsages) {
      this.prevMember = prevMember;
      this.brokenUsages = brokenUsages;
    }
  }
}
