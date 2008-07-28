package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;

/**
 * @author yole
 */
public class JavaStringContextType extends AbstractContextType {
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

  protected LanguageFileType getExpectedFileType() {
    return null;
  }

  protected TemplateContext.ContextElement getContextElement(final TemplateContext context) {
    return context.JAVA_STRING;
  }
}
