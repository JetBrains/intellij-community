package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class OtherContextType extends TemplateContextType {
  public OtherContextType() {
    super("OTHER", CodeInsightBundle.message("dialog.edit.template.checkbox.other"));
  }

  public boolean isInContext(@NotNull final FileType fileType) {
    return true;
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return true;
  }

}
