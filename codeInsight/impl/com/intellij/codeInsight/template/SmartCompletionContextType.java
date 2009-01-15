package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class SmartCompletionContextType extends TemplateContextType {
  public SmartCompletionContextType() {
    super("COMPLETION", CodeInsightBundle.message("dialog.edit.template.checkbox.smart.type.completion"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return false;
  }

  @Override
  public boolean isInContext(@NotNull FileType fileType) {
    return false;
  }

  @Override
  public boolean isExpandableFromEditor() {
    return false;
  }

}
