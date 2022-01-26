// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.problems.*;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.codeInsight.hints.InlayHintsUtilsKt.addCodeVisionElement;
import static com.intellij.util.ObjectUtils.tryCast;

public class ProjectProblemHintProvider implements InlayHintsProvider<NoSettings> {

  @Nullable
  @Override
  public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                             @NotNull Editor editor,
                                             @NotNull NoSettings settings,
                                             @NotNull InlayHintsSink sink) {
    return new InlayHintsCollector() {
      @Override
      public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
        PsiJavaFile file = tryCast(element.getContainingFile(), PsiJavaFile.class);
        if (file == null) return true;
        Project project = file.getProject();
        reportPreviewProblem(editor, element, sink);
        FileState prevState = FileStateUpdater.getState(file);
        if (prevState == null) return false;
        Map<PsiMember, Set<Problem>> problems = ProjectProblemUtils.getReportedProblems(editor);
        Map<PsiMember, ScopedMember> prevChanges = getPrevChanges(prevState.getChanges(), problems.keySet());
        FileState curState = FileStateUpdater.findState(file, prevState.getSnapshot(), prevChanges);
        Map<PsiMember, ScopedMember> changes = curState.getChanges();
        Map<SmartPsiElementPointer<PsiMember>, ScopedMember> snapshot = curState.getSnapshot();
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        boolean isInSplitEditorMode = editorManager.getSelectedEditors().length > 1;
        collectProblems(changes, prevState.getChanges(), problems, isInSplitEditorMode);

        ProjectProblemUtils.reportProblems(editor, problems);
        Map<PsiMember, ScopedMember> allChanges = new HashMap<>(changes);
        prevChanges.forEach((key, value) -> allChanges.putIfAbsent(key, value));
        FileState fileState = new FileState(snapshot, allChanges);
        FileStateUpdater.updateState(file, fileState);

        PresentationFactory factory = new PresentationFactory((EditorImpl)editor);
        Document document = editor.getDocument();
        List<HighlightInfo> highlighters = new SmartList<>();
        problems.forEach((psiMember, memberProblems) -> {
          PsiNameIdentifierOwner namedElement = tryCast(psiMember, PsiNameIdentifierOwner.class);
          if (namedElement == null) return;
          PsiElement identifier = namedElement.getNameIdentifier();
          if (identifier == null) return;
          addInlay(editor, sink, project, factory, psiMember, memberProblems);
          highlighters.add(ProjectProblemUtils.createHighlightInfo(editor, psiMember, identifier));
        });

        ApplicationManager.getApplication().invokeLater(() -> {
          if (project.isDisposed() || !file.isValid()) return;
          int fileTextLength = file.getTextLength();
          EditorColorsScheme colorsScheme = editor.getColorsScheme();
          UpdateHighlightersUtil.setHighlightersToEditor(project, document, 0, fileTextLength, highlighters, colorsScheme, -1);
        }, ModalityState.NON_MODAL);

        return false;
      }

      private @NotNull Map<PsiMember, ScopedMember> getPrevChanges(@NotNull Map<PsiMember, ScopedMember> prevChanges,
                                                                   @NotNull Set<PsiMember> reportedMembers) {
        if (reportedMembers.isEmpty()) return prevChanges;
        HashMap<PsiMember, ScopedMember> changes = new HashMap<>(prevChanges);
        reportedMembers.forEach(m -> changes.putIfAbsent(m, null));
        return changes;
      }

      private void collectProblems(@NotNull Map<PsiMember, ScopedMember> changes,
                                   @NotNull Map<PsiMember, ScopedMember> oldChanges,
                                   @NotNull Map<PsiMember, Set<Problem>> oldProblems,
                                   boolean isInSplitEditorMode) {
        if (isInSplitEditorMode && changes.isEmpty()) {
          changes = oldChanges;
        }

        changes.forEach((curMember, prevMember) -> {
          if (hasOtherElementsOnSameLine(curMember)) {
            oldProblems.remove(curMember);
            return;
          }
          Set<Problem> memberProblems = ProblemCollector.collect(prevMember, curMember);
          if (memberProblems == null || memberProblems.isEmpty()) {
            oldProblems.remove(curMember);
          }
          else {
            oldProblems.put(curMember, memberProblems);
          }
        });
      }

      private boolean hasOtherElementsOnSameLine(@NotNull PsiMember psiMember) {
        PsiElement prevSibling = psiMember.getPrevSibling();
        while (prevSibling != null && !(prevSibling instanceof PsiWhiteSpace && prevSibling.textContains('\n'))) {
          if (!(prevSibling instanceof PsiWhiteSpace) && !prevSibling.getText().isEmpty()) return true;
          prevSibling = prevSibling.getPrevSibling();
        }
        return false;
      }
    };
  }

  private static void addInlay(@NotNull Editor editor,
                               @NotNull InlayHintsSink sink,
                               Project project,
                               PresentationFactory factory,
                               PsiMember psiMember,
                               Set<Problem> memberProblems) {
    int offset = ProjectProblemUtils.getMemberOffset(psiMember);
    InlayPresentation presentation = ProjectProblemUtils.getPresentation(project, editor, factory, psiMember, memberProblems);

    addCodeVisionElement(sink, editor, offset, BlockInlayPriority.PROBLEMS, presentation);
  }

  @NotNull
  @Override
  public NoSettings createSettings() {
    return new NoSettings();
  }

  @NotNull
  @Override
  public @Nls String getName() {
    return JavaBundle.message("project.problems.title");
  }

  @Override
  public @NotNull InlayGroup getGroup() {
    return InlayGroup.CODE_VISION_GROUP;
  }

  private static final SettingsKey<NoSettings> KEY = new SettingsKey<>("RelatedProblems");

  @NotNull
  @Override
  public SettingsKey<NoSettings> getKey() {
    return KEY;
  }

  static boolean hintsEnabled() {
    return InlayHintsSettings.instance().hintsEnabled(KEY, JavaLanguage.INSTANCE);
  }

  @Nullable
  @Override
  public String getPreviewText() {
    return null;
  }

  @Nls
  @Nullable
  @Override
  public String getProperty(@NotNull String key) {
    return JavaBundle.message(key);
  }

  @NotNull
  @Override
  public ImmediateConfigurable createConfigurable(@NotNull NoSettings settings) {
    return new ImmediateConfigurable() {
      @NotNull
      @Override
      public JComponent createComponent(@NotNull ChangeListener listener) {
        JPanel panel = new JPanel();
        panel.setVisible(false);
        return panel;
      }
    };
  }

  @Override
  public boolean isLanguageSupported(@NotNull Language language) {
    return true;
  }

  @Override
  public boolean isVisibleInSettings() {
    return true;
  }

  private static final Key<Set<Problem>> PREVIEW_PROBLEMS_KEY = Key.create("preview.problems.key");

  @Override
  public void preparePreview(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiMethod method = ((PsiJavaFile)file).getClasses()[0].getMethods()[0];
    method.putUserData(PREVIEW_PROBLEMS_KEY, Collections.singleton(new Problem(method, method)));
  }

  private static void reportPreviewProblem(@NotNull Editor editor,
                                           @NotNull PsiElement psiElement,
                                           @NotNull InlayHintsSink sink) {
    Set<Problem> problems = PREVIEW_PROBLEMS_KEY.get(psiElement);
    if (problems != null) {
      addInlay(editor, sink, psiElement.getProject(), new PresentationFactory((EditorImpl)editor), (PsiMember)psiElement, problems);
    }
  }

  static @NotNull List<AnAction> getPopupActions() {
    return InlayHintsUtils.INSTANCE.getDefaultInlayHintsProviderPopupActions(
      KEY, JavaBundle.messagePointer("title.related.problems.inlay.hints")
    );
  }
}
