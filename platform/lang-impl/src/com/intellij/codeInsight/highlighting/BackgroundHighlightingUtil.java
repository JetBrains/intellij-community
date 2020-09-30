// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorActivityManager;
import com.intellij.openapi.editor.ex.EditorEx;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.function.BiFunction;

public class BackgroundHighlightingUtil {
  /**
   * start background thread where find injected fragment at the caret position,
   * invoke {@code backgroundProcessor} on that fragment and invoke later {@code edtProcessor} in EDT,
   * cancel if anything changes.
   */
  static <T> void lookForInjectedFileInOtherThread(@NotNull Project project,
                                                   @NotNull Editor editor,
                                                   @NotNull BiFunction<? super PsiFile, ? super EditorEx, ? extends T> backgroundProcessor,
                                                   @NotNull TriConsumer<? super PsiFile, ? super EditorEx, ? super T> edtProcessor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
        EditorEx newEditor = (EditorEx)InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, newFile);
        T result = backgroundProcessor.apply(newFile, newEditor);
        return Trinity.create(newFile, newEditor, result);
      })
      .withDocumentsCommitted(project)
      .expireWhen(() -> !isValidEditor(editor))
      .coalesceBy(BackgroundHighlightingUtil.class, editor)
      .finishOnUiThread(ModalityState.stateForComponent(editor.getComponent()), t -> {
        if (t == null) return;
        PsiFile foundFile = t.getFirst();
        EditorEx newEditor = t.getSecond();
        T result = t.getThird();
        if (foundFile == null) return;

        if (foundFile.isValid() && offsetBefore == editor.getCaretModel().getOffset()) {
          edtProcessor.accept(foundFile, newEditor, result);
        }
        else {
          lookForInjectedFileInOtherThread(project, editor, backgroundProcessor, edtProcessor);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static boolean isValidEditor(@NotNull Editor editor) {
    Project editorProject = editor.getProject();
    return editorProject != null && !editorProject.isDisposed() && !editor.isDisposed() &&
           EditorActivityManager.getInstance().isVisible(editor);
  }

  @NotNull
  private static PsiFile getInjectedFileIfAny(int offset, @NotNull PsiFile psiFile) {
    PsiElement injectedElement = InjectedLanguageManager.getInstance(psiFile.getProject()).findInjectedElementAt(psiFile, offset);
    if (injectedElement != null) {
      PsiFile injected = injectedElement.getContainingFile();
      if (injected != null) {
        return injected;
      }
    }
    return psiFile;
  }

  @TestOnly
  public static void enableListenersInTest(@NotNull Project project, @NotNull Disposable disposable) {
    BackgroundHighlighter.enableListenersInTest(project, disposable);
  }
}
