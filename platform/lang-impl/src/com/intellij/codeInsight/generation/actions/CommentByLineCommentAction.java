package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByLineCommentHandler;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.lang.LanguageCommenters;

public class CommentByLineCommentAction extends BaseCodeInsightAction {
  public CommentByLineCommentAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new CommentByLineCommentHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof AbstractFileType) {
      return ((AbstractFileType)fileType).getCommenter() != null;
    }
    return LanguageCommenters.INSTANCE.forLanguage(file.getLanguage()) != null || LanguageCommenters.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage()) != null;
  }
}
