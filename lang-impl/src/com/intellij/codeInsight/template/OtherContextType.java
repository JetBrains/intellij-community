package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;

/**
 * @author yole
 */
public class OtherContextType extends AbstractContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.other");
  }

  public boolean isInContext(final FileType fileType) {
    return true;
  }

  protected TemplateContext.ContextElement getContextElement(final TemplateContext context) {
    return context.OTHER;
  }

  protected LanguageFileType getExpectedFileType() {
    return null;
  }
}
