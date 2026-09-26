// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.extensions.ExtensionPointName;


public interface TemplateCompletionProcessor {
  ExtensionPointName<TemplateCompletionProcessor> EP_NAME = ExtensionPointName.create("com.intellij.templateCompletionProcessor");

  boolean nextTabOnItemSelected(ExpressionContext context, final LookupElement item);
}
