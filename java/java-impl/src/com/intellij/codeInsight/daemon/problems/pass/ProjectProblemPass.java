// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.problems.*;
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
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils.*;

public class ProjectProblemPass extends EditorBoundHighlightingPass {

  private final FileEditorManager myEditorManager = FileEditorManager.getInstance(myProject);

  private Map<PsiMember, MemberState> myMemberStates = null;
  private Map<SmartPsiElementPointer<PsiMember>, ScopedMember> mySnapshot = null;

  ProjectProblemPass(@NotNull Editor editor, @NotNull PsiJavaFile file) {
    super(editor, file, true);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    FileState prevState = FileStateUpdater.getState(myFile);
    if (prevState == null) return;
    FileState curState = FileStateUpdater.findState(myFile, prevState.getSnapshot());
    myMemberStates = collectStates(curState.getChanges(), prevState.getChanges());
    mySnapshot = curState.getSnapshot();
  }

  @Override
  public void doApplyInformationToEditor() {
    Map<SmartPsiElementPointer<PsiMember>, ScopedMember> snapshot = mySnapshot;
    if (snapshot == null) return;
    Map<PsiMember, MemberState> memberStates = myMemberStates;
    if (memberStates == null) return;

    PresentationFactory factory = new PresentationFactory((EditorImpl)myEditor);
    Map<PsiMember, EditorInfo> editorInfos = getEditorInfos(myEditor);
    Map<PsiMember, ScopedMember> changes = new SmartHashMap<>();
    memberStates.forEach((curMember, state) -> {
      ScopedMember prevMember = state.prevMember;
      Set<Problem> relatedProblems = state.relatedProblems;
      changes.put(curMember, prevMember);
      if (relatedProblems != null) addInfo(factory, curMember, relatedProblems, editorInfos);
    });
    updateInfos(myEditor, editorInfos);

    List<HighlightInfo> highlighters = ContainerUtil.map(editorInfos.values(), v -> v.myHighlightInfo);
    int textLength = myFile.getTextLength();
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, textLength, highlighters, myEditor.getColorsScheme(), -1);

    FileState fileState = new FileState(snapshot, changes);
    FileStateUpdater.updateState(myFile, fileState);
    updateTimestamp(myEditor);
  }

  private void addInfo(@NotNull PresentationFactory factory,
                       @NotNull PsiMember psiMember,
                       @NotNull Set<Problem> relatedProblems,
                       @NotNull Map<PsiMember, EditorInfo> editorInfos) {
    EditorInfo oldInfo = editorInfos.remove(psiMember);
    if (oldInfo != null) Disposer.dispose(oldInfo.myInlay);
    if (relatedProblems.isEmpty() || hasOtherElementsOnSameLine(psiMember)) return;
    PsiElement identifier = getIdentifier(psiMember);
    if (identifier == null) return;
    int offset = getMemberOffset(psiMember);
    InlayPresentation presentation = getPresentation(myProject, myEditor, myEditor.getDocument(), factory, offset, psiMember, relatedProblems);
    BlockInlayRenderer renderer = createBlockRenderer(presentation);
    Inlay<?> newInlay = myEditor.getInlayModel().addBlockElement(offset, true, true, BlockInlayPriority.PROBLEMS, renderer);
    if (newInlay == null) return;
    addListener(renderer, newInlay);
    HighlightInfo newHighlightInfo = createHighlightInfo(myEditor, psiMember, identifier, relatedProblems);
    editorInfos.put(psiMember, new EditorInfo(newInlay, newHighlightInfo));
  }

  private @NotNull Map<PsiMember, MemberState> collectStates(@NotNull Map<PsiMember, ScopedMember> curChanges,
                                                             @NotNull Map<PsiMember, ScopedMember> oldChanges) {
    boolean isInSplitEditorMode = myEditorManager.getSelectedEditors().length > 1;
    if (isInSplitEditorMode && curChanges.isEmpty() && !isDocumentUpdated(myEditor)) {
      // some other file changed. this change might be a new broken usage of one of members in current file.
      // so, now we need to recheck all of the previous changes.
      curChanges = oldChanges;
      oldChanges = Collections.emptyMap();
    }
    Map<PsiMember, MemberState> states = ContainerUtil.map2Map(oldChanges.entrySet(),
                                                               e -> Pair.create(e.getKey(), new MemberState(e.getValue(), null)));
    curChanges.forEach((curMember, prevMember) -> {
      Set<Problem> changeProblems = ProblemCollector.collect(prevMember, curMember);
      if (changeProblems == null) changeProblems = Collections.emptySet();
      states.put(curMember, new MemberState(prevMember, changeProblems));
    });
    return states;
  }

  private static final class MemberState {
    private final ScopedMember prevMember;
    private final Set<Problem> relatedProblems;

    private MemberState(ScopedMember prevMember, Set<Problem> relatedProblems) {
      this.prevMember = prevMember;
      this.relatedProblems = relatedProblems;
    }
  }
}
