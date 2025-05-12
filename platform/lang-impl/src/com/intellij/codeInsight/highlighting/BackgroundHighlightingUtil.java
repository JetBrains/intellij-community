// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.idea.AppModeAssertions.checkFrontend;
import static com.intellij.openapi.editor.rd.LocalEditorSupportUtil.isLocalEditorSupport;

@ApiStatus.Internal
public final class BackgroundHighlightingUtil {
  /**
   * Add this key to the {@link Editor}'s user data to prohibit running all background highlighting activities.
   */
  private static final Key<Boolean> IGNORE_EDITOR = Key.create("BackgroundHighlightingUtil.IGNORE_EDITOR");
  public static void disableBackgroundHighlightingForeverIn(@NotNull Editor editor) {
    editor.putUserData(IGNORE_EDITOR, true);
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  static Pair<PsiFile, Editor> findInjected(@NotNull Editor editor, @NotNull Project project, int offsetBefore) {
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
    return Pair.create(newFile, newEditor);
  }

  static boolean isValidEditor(@NotNull Editor editor) {
    Project editorProject = editor.getProject();
    return editorProject != null &&
           !editorProject.isDisposed() &&
           !editor.isDisposed() &&
           !Boolean.TRUE.equals(editor.getUserData(IGNORE_EDITOR)) &&
           UIUtil.isShowing(editor.getContentComponent());
  }

  static boolean needMatching(@NotNull Editor newEditor, @NotNull CodeInsightSettings codeInsightSettings) {
    if (isLocalEditorSupport(newEditor)) {
      return checkFrontend();
    }
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
