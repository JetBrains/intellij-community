// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.problems.*;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        FileState prevState = FileStateUpdater.getState(file);
        if (prevState == null) return false;
        FileState curState = FileStateUpdater.findState(file, prevState.getSnapshot());
        Map<PsiMember, ScopedMember> changes = curState.getChanges();
        Map<SmartPsiElementPointer<PsiMember>, ScopedMember> snapshot = curState.getSnapshot();
        Map<PsiMember, Set<Problem>> problems = ProjectProblemUtils.getReportedProblems(editor);
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        boolean isInSplitEditorMode = editorManager.getSelectedEditors().length > 1;
        collectProblems(changes, prevState.getChanges(), problems, isInSplitEditorMode);

        ProjectProblemUtils.reportProblems(editor, problems);
        if (!isInSplitEditorMode || getSelectedEditor(editorManager) == editor) {
          FileState fileState = new FileState(snapshot, changes);
          FileStateUpdater.updateState(file, fileState);
        }

        PresentationFactory factory = new PresentationFactory((EditorImpl)editor);
        Document document = editor.getDocument();
        List<HighlightInfo> highlighters = new SmartList<>();
        problems.forEach((psiMember, memberProblems) -> {
          PsiNameIdentifierOwner namedElement = tryCast(psiMember, PsiNameIdentifierOwner.class);
          if (namedElement == null) return;
          PsiElement identifier = namedElement.getNameIdentifier();
          if (identifier == null) return;
          int offset = ProjectProblemUtils.getMemberOffset(psiMember);
          InlayPresentation presentation =
            ProjectProblemUtils.getPresentation(project, editor, document, factory, offset, psiMember, memberProblems);
          sink.addBlockElement(offset, true, true, BlockInlayPriority.PROBLEMS, presentation);
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

      private @Nullable Editor getSelectedEditor(@NotNull FileEditorManager manager) {
        TextEditor textEditor = tryCast(manager.getSelectedEditor(), TextEditor.class);
        return textEditor == null ? null : textEditor.getEditor();
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

  private static final String RELATED_PROBLEMS_ID = "RelatedProblems";
  private static final SettingsKey<NoSettings> KEY = new SettingsKey<>(RELATED_PROBLEMS_ID);

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
    return false;
  }

  static void openSettings(@NotNull Project project) {
    InlayHintsConfigurable.showSettingsDialogForLanguage(project, JavaLanguage.INSTANCE,
                                                         model -> model.getId().equals(RELATED_PROBLEMS_ID));
  }
}
