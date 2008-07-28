package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author yole
 */
public class JavaCommentContextType extends AbstractContextType {
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

  protected LanguageFileType getExpectedFileType() {
    return null;
  }

  protected TemplateContext.ContextElement getContextElement(final TemplateContext context) {
    return context.JAVA_COMMENT;
  }
}
