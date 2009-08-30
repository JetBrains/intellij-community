package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaStringContextType extends TemplateContextType {
  public JavaStringContextType() {
    super("JAVA_STRING", CodeInsightBundle.message("dialog.edit.template.checkbox.java.string"));
  }

  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA) {
      PsiElement element = file.findElementAt(offset);
      return element instanceof PsiJavaToken && ((PsiJavaToken) element).getTokenType() == JavaTokenType.STRING_LITERAL;
    }
    return false;
  }

  @Override
  public boolean isInContext(@NotNull FileType fileType) {
    return false;
  }


}
