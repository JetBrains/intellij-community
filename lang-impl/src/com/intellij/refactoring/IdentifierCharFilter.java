/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class IdentifierCharFilter extends CharFilter {

  public Result acceptChar(char c, @NotNull final int prefixLength, final Lookup lookup) {
    if (lookup.isCompletion()) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
  }
}
