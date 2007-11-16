package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByBlockCommentHandler;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.Language;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
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
    if (fileType instanceof CustomFileType) {
      return ((CustomFileType)fileType).getCommenter() != null;
    }

    final Language lang = file.getLanguage();
    if (lang == null) return false;
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(lang);
    if (commenter == null) return false;
    return commenter.getBlockCommentPrefix() != null && commenter.getBlockCommentSuffix() != null;
  }
}