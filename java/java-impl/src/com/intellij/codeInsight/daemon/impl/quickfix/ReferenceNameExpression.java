// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.util.text.EditDistance;

import java.util.Arrays;
import java.util.Comparator;

public class ReferenceNameExpression extends Expression {
  private final LookupElement[] myItems;
  private final String myOldReferenceName;

  public ReferenceNameExpression(LookupElement[] items, String oldReferenceName) {
    myItems = items;
    myOldReferenceName = oldReferenceName;
    Arrays.sort(myItems, new DifferenceComparator());
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    return myItems == null || myItems.length == 0 ? new TextResult(myOldReferenceName) : new TextResult(myItems[0].getLookupString());
  }

  @Override
  public Result calculateQuickResult(ExpressionContext context) {
    return null;
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return myItems == null || myItems.length == 1 ? null : myItems;
  }

  private class DifferenceComparator implements Comparator<LookupElement> {
    @Override
    public int compare(LookupElement lookupItem1, LookupElement lookupItem2) {
      String s1 = lookupItem1.getLookupString();
      String s2 = lookupItem2.getLookupString();
      int length = myOldReferenceName.length();
      int length1 = s1.length();
      int length2 = s2.length();
      if (length < length1) s1 = s1.substring(0, length + 1);
      if (length < length2) s2 = s2.substring(0, length + 1);
      int diff1 = EditDistance.optimalAlignment(s1, myOldReferenceName, false);
      int diff2 = EditDistance.optimalAlignment(s2, myOldReferenceName, false);
      final int diff = diff1 - diff2;
      if (diff == 0) {
        // prefer shorter string
        if (length1 < length2) return -1;
        else if (length1 > length2) return +1;
        final char c = myOldReferenceName.charAt(0);
        if (s1.charAt(0) == c) return -1;
        else if (s2.charAt(0) == c) return +1;
      }
      return diff;
    }
  }
}
