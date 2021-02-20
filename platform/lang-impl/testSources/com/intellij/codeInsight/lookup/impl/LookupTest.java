// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import javax.swing.*;

public class LookupTest extends LightPlatformCodeInsightTestCase {
  public void testLookupSize() {
    configureFromFileText("test.txt", "");
    int smallWidth = getLookupWidth("Short");
    int longWidth = getLookupWidth("A long long long long long long text");
    assertTrue(longWidth > smallWidth);
  }

  private int getLookupWidth(String string) {
    LookupImpl lookup = (LookupImpl)LookupManager.getInstance(getProject()).showLookup(
      getEditor(),
      LookupElementBuilder.create(string)
    );
    JList<?> list = lookup.getList();
    list.setSize(1, 1); // Make it visible
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    return lookup.myCellRenderer.getLookupTextWidth();
  }
}
