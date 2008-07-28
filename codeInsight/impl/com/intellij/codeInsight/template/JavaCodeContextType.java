package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;

public class JavaCodeContextType implements TemplateContextType {
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

  public boolean isInContext(final FileType fileType) {
    return fileType == StdFileTypes.JAVA;
  }

  public boolean isEnabled(final TemplateContext context) {
    return context.JAVA_CODE;
  }

  public void setEnabled(final TemplateContext context, final boolean value) {
    context.JAVA_CODE = value;
  }

  public SyntaxHighlighter createHighlighter() {
     return SyntaxHighlighter.PROVIDER.create(StdFileTypes.JAVA, null, null);
  }
}
