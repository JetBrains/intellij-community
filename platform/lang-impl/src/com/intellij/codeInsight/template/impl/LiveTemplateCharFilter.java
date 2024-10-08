// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class LiveTemplateCharFilter extends CharFilter {
  @Override
  public Result acceptChar(char c, int prefixLength, Lookup lookup) {
    LookupElement item = lookup.getCurrentItem();
    if (item instanceof LiveTemplateLookupElement && lookup.isCompletion()) {
      if (Character.isJavaIdentifierPart(c) || c == '%') return Result.ADD_TO_PREFIX;

      if (c == ((LiveTemplateLookupElement)item).getTemplateShortcut()) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      return null;
    }
    if (item instanceof TemplateExpressionLookupElement) {
      if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
      if (CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars()) {
        return null;
      }
      return Result.HIDE_LOOKUP;
    }

    return null;
  }
}
