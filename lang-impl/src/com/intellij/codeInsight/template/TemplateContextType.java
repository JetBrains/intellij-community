package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface TemplateContextType {
  ExtensionPointName<TemplateContextType> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateContext");
  
  String getName();
  boolean isInContext(PsiFile file, int offset);

  // these methods mostly exist for serialization compatibility with pre-8.0 live templates
  boolean isEnabled(TemplateContext context);
  void setEnabled(TemplateContext context, boolean value);
}
