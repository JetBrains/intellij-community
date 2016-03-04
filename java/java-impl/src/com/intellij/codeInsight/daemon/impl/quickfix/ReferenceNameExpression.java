/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;

import java.util.Arrays;
import java.util.Comparator;

public class ReferenceNameExpression extends Expression {
  class HammingComparator implements Comparator<LookupElement> {
    @Override
    public int compare(LookupElement lookupItem1, LookupElement lookupItem2) {
      String s1 = lookupItem1.getLookupString();
      String s2 = lookupItem2.getLookupString();
      int diff1 = 0;
      for (int i = 0; i < Math.min(s1.length(), myOldReferenceName.length()); i++) {
        if (s1.charAt(i) != myOldReferenceName.charAt(i)) diff1++;
      }
      int diff2 = 0;
      for (int i = 0; i < Math.min(s2.length(), myOldReferenceName.length()); i++) {
        if (s2.charAt(i) != myOldReferenceName.charAt(i)) diff2++;
      }
      return diff1 - diff2;
    }
  }

  public ReferenceNameExpression(LookupElement[] items, String oldReferenceName) {
    myItems = items;
    myOldReferenceName = oldReferenceName;
    Arrays.sort(myItems, new HammingComparator());
  }

  LookupElement[] myItems;
  private final String myOldReferenceName;

  @Override
  public Result calculateResult(ExpressionContext context) {
    if (myItems == null || myItems.length == 0) {
      return new TextResult(myOldReferenceName);
    }
    return new TextResult(myItems[0].getLookupString());
  }

  @Override
  public Result calculateQuickResult(ExpressionContext context) {
    return null;
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    if (myItems == null || myItems.length == 1) return null;
    return myItems;
  }
}
