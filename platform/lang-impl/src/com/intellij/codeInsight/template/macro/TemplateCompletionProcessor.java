package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface TemplateCompletionProcessor {
  ExtensionPointName<TemplateCompletionProcessor> EP_NAME = ExtensionPointName.create("com.intellij.templateCompletionProcessor");

  boolean nextTabOnItemSelected(ExpressionContext context, final LookupElement item);
}
