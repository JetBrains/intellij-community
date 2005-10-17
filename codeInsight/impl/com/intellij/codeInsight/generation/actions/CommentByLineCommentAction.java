package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByLineCommentHandler;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class CommentByLineCommentAction extends BaseCodeInsightAction {
  public CommentByLineCommentAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new CommentByLineCommentHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof CustomFileType) {
      return ((CustomFileType)fileType).getCommenter() != null;
    }
    final Language lang = file.getLanguage();
    return lang != null && lang.getCommenter() != null;
  }
}
