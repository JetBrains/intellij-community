package com.intellij.codeInsight.template;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.JavaTokenType;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;

/**
 * @author yole
 */
public class JavaStringContextType implements TemplateContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.java.string");
  }

  public boolean isInContext(final PsiFile file, final int offset) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      PsiElement element = file.findElementAt(offset);
      return element instanceof PsiJavaToken && ((PsiJavaToken) element).getTokenType() == JavaTokenType.STRING_LITERAL;
    }
    return false;
  }// these methods mostly exist for serialization compatibility with pre-8.0 live templates

  public boolean isEnabled(final TemplateContext context) {
    return context.JAVA_STRING;
  }

  public void setEnabled(final TemplateContext context, final boolean value) {
    context.JAVA_STRING = value;
  }

  public SyntaxHighlighter createHighlighter() {
    return null;
  }
}
