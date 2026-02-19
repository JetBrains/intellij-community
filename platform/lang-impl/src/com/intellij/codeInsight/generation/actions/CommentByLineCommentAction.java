// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.actions.MultiCaretCodeInsightAction;
import com.intellij.codeInsight.actions.MultiCaretCodeInsightActionHandler;
import com.intellij.codeInsight.generation.CommentByLineCommentHandler;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class CommentByLineCommentAction extends MultiCaretCodeInsightAction implements DumbAware, LightEditCompatible {
  public CommentByLineCommentAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected @NotNull MultiCaretCodeInsightActionHandler getHandler() {
    return new CommentByLineCommentHandler();
  }

  @Override
  protected boolean isValidFor(@NotNull Project project, @NotNull Editor editor, @NotNull Caret caret, final @NotNull PsiFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof AbstractFileType) {
      return ((AbstractFileType)fileType).getCommenter() != null;
    }

    if (LanguageCommenters.INSTANCE.forLanguage(file.getLanguage()) != null ||
        LanguageCommenters.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage()) != null) return true;
    PsiElement host = InjectedLanguageManager.getInstance(project).getInjectionHost(file);
    return host != null && LanguageCommenters.INSTANCE.forLanguage(host.getLanguage()) != null;
  }
}
