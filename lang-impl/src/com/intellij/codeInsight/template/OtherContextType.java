package com.intellij.codeInsight.template;

import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;

/**
 * @author yole
 */
public class OtherContextType implements TemplateContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.other");
  }

  public boolean isInContext(final PsiFile file, final int offset) {
    return true;
  }

  public boolean isEnabled(final TemplateContext context) {
    return context.OTHER;
  }

  public void setEnabled(final TemplateContext context, final boolean value) {
    context.OTHER = value;
  }

  public SyntaxHighlighter createHighlighter() {
    return null;
  }
}
