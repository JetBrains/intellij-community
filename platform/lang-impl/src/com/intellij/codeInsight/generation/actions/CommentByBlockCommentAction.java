package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByBlockCommentHandler;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class CommentByBlockCommentAction extends BaseCodeInsightAction {
  public CommentByBlockCommentAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new CommentByBlockCommentHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof AbstractFileType) {
      return ((AbstractFileType)fileType).getCommenter() != null;
    }

    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(file.getLanguage());
    if (commenter == null) commenter = LanguageCommenters.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage());
    if (commenter == null) return false;
    return commenter.getBlockCommentPrefix() != null && commenter.getBlockCommentSuffix() != null;
  }
}