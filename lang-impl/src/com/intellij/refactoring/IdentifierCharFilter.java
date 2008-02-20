/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.lookup.CharFilter;

/**
 * @author peter
*/
public class IdentifierCharFilter implements CharFilter {
  public static final IdentifierCharFilter INSTANCE = new IdentifierCharFilter();

  private IdentifierCharFilter() {
  }

  public int accept(char c, final String prefix) {
    if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
    return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
  }
}
