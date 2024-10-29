// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DefaultCharFilter extends CharFilter {

  @Override
  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    return switch (c) {
      case '.', ',', ';', '=', ' ', ':', '(' -> Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      case '-' -> ContainerUtil.exists(lookup.getItems(), item -> matchesAfterAppendingChar(lookup, item, c))
                  ? Result.ADD_TO_PREFIX
                  : Result.HIDE_LOOKUP;
      default -> Result.HIDE_LOOKUP;
    };
  }

  private static boolean matchesAfterAppendingChar(Lookup lookup, LookupElement item, char c) {
    PrefixMatcher matcher = lookup.itemMatcher(item);
    return matcher.cloneWithPrefix((matcher.getPrefix() + ((LookupImpl)lookup).getAdditionalPrefix()) + c).prefixMatches(item);
  }
}