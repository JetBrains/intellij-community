package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;

public class JavaCodeContextType extends AbstractContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.java.code");
  }

  public boolean isInContext(final PsiFile file, final int offset) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      PsiElement element = file.findElementAt(offset);
      return element != null && PsiTreeUtil.getParentOfType(element, PsiComment.class) == null &&
             !(element instanceof PsiJavaToken && ((PsiJavaToken) element).getTokenType() == JavaTokenType.STRING_LITERAL);
    }
    if (fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX) {
      final Language language = PsiUtilBase.getLanguageAtOffset(file, offset);
      return language.equals(StdLanguages.JAVA);
    }
    return false;
  }

  @Override
  public boolean isInContext(final FileType fileType) {
    return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX ;
  }

  protected LanguageFileType getExpectedFileType() {
    return StdFileTypes.JAVA;
  }

  protected TemplateContext.ContextElement getContextElement(final TemplateContext context) {
    return context.JAVA_CODE;
  }
}
