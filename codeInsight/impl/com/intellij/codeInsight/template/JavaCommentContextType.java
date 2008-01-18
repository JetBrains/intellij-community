package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author yole
 */
public class JavaCommentContextType implements TemplateContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.java.comment");
  }

  public boolean isInContext(final PsiFile file, final int offset) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      PsiElement element = file.findElementAt(offset);
      return PsiTreeUtil.getParentOfType(element, PsiComment.class) != null;
    }
    return false;
  }

  public boolean isEnabled(final TemplateContext context) {
    return context.JAVA_COMMENT;
  }

  public void setEnabled(final TemplateContext context, final boolean value) {
    context.JAVA_COMMENT = value;
  }

  public SyntaxHighlighter createHighlighter() {
     return null;
  }
}
