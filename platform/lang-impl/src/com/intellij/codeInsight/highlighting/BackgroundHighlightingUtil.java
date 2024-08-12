// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.TriConsumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public final class BackgroundHighlightingUtil {
  /**
   * start background thread where find injected fragment at the caret position,
   * invoke {@code backgroundProcessor} on that fragment and invoke later {@code edtProcessor} in EDT,
   * cancel if anything changes.
   */
  static <T> void lookForInjectedFileInOtherThread(@NotNull Project project,
                                                   @NotNull Editor editor,
                                                   @NotNull BiFunction<? super PsiFile, ? super Editor, ? extends T> backgroundProcessor,
                                                   @NotNull TriConsumer<? super PsiFile, ? super Editor, ? super T> edtProcessor) {
    ThreadingAssertions.assertEventDispatchThread();
    assert !(editor instanceof EditorWindow) : editor;
    if (!isValidEditor(editor)) return;

    int offsetBefore = editor.getCaretModel().getOffset();

    ReadAction
      .nonBlocking(() -> {
        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (psiFile == null) return null;
        if (psiFile instanceof PsiCompiledFile) {
          psiFile = ((PsiCompiledFile)psiFile).getDecompiledPsiFile();
        }
        if (psiFile instanceof PsiBinaryFile && BinaryFileTypeDecompilers.getInstance().forFileType(psiFile.getFileType()) == null) {
          return null;
        }
        PsiFile newFile = getInjectedFileIfAny(offsetBefore, psiFile);
        Editor newEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, newFile);
        T result = backgroundProcessor.apply(newFile, newEditor);
        return Trinity.create(newFile, newEditor, result);
      })
      .withDocumentsCommitted(project)
      .expireWhen(() -> !isValidEditor(editor))
      .coalesceBy(BackgroundHighlightingUtil.class, editor)
      .finishOnUiThread(ModalityState.stateForComponent(editor.getComponent()), t -> {
        if (t == null) return;
        PsiFile foundFile = t.getFirst();
        if (foundFile == null) return;
        if (foundFile.isValid() && offsetBefore == editor.getCaretModel().getOffset()) {
          Editor newEditor = t.getSecond();
          T result = t.getThird();
          edtProcessor.accept(foundFile, newEditor, result);
        }
        else {
          lookForInjectedFileInOtherThread(project, editor, backgroundProcessor, edtProcessor);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  static boolean isValidEditor(@NotNull Editor editor) {
    Project editorProject = editor.getProject();
    return editorProject != null && !editorProject.isDisposed() && !editor.isDisposed() &&
           UIUtil.isShowing(editor.getContentComponent());
  }

  static boolean needMatching(@NotNull Editor newEditor, @NotNull CodeInsightSettings codeInsightSettings) {
    if (!codeInsightSettings.HIGHLIGHT_BRACES) return false;

    if (newEditor.getSelectionModel().hasSelection()) return false;

    if (newEditor.getSoftWrapModel().isInsideOrBeforeSoftWrap(newEditor.getCaretModel().getVisualPosition())) return false;

    TemplateState state = TemplateManagerImpl.getTemplateState(newEditor);
    return state == null || state.isFinished();
  }

  private static @NotNull PsiFile getInjectedFileIfAny(int offset, @NotNull PsiFile psiFile) {
    PsiElement injectedElement = InjectedLanguageManager.getInstance(psiFile.getProject()).findInjectedElementAt(psiFile, offset);
    if (injectedElement != null) {
      PsiFile injected = injectedElement.getContainingFile();
      if (injected != null) {
        return injected;
      }
    }
    return psiFile;
  }
}
