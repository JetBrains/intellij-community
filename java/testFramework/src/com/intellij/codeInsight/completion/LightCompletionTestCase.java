// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class LightCompletionTestCase extends LightJavaCodeInsightTestCase {
  protected String myPrefix;
  protected LookupElement[] myItems;
  private CompletionType myType = CompletionType.BASIC;

  @Override
  protected void tearDown() throws Exception {
    try {
      myItems = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {

      super.tearDown();
    }
  }

  @Override
  protected void configureByFile(@NotNull String relativePath) {
    super.configureByFile(relativePath);

    complete();
  }

  protected void complete() {
    complete(1);
  }

  protected void complete(final int time) {
    new CodeCompletionHandlerBase(myType).invokeCompletion(getProject(), getEditor(), time);

    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(getEditor());
    myItems = lookup == null ? null : lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    myPrefix = lookup == null ? null : lookup.itemPattern(lookup.getItems().get(0));
  }

  public void setType(CompletionType type) {
    myType = type;
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }

  protected void selectItem(LookupElement item, char completionChar) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
    assertNotNull(lookup);
    lookup.setCurrentItem(item);
    if (completionChar == 0 || completionChar == '\n' || completionChar == '\t') {
      lookup.finishLookup(completionChar);
    } else {
      type(completionChar);
    }
  }

  protected void testByCount(int finalCount, String... values) {
    if (myItems == null) {
      assertEquals(finalCount, 0);
      return;
    }
    int index = 0;
    for (final LookupElement myItem : myItems) {
      for (String value : values) {
        if (value == null) {
          fail("Unacceptable value reached: " + myItem.getLookupString());
        }
        if (value.equals(myItem.getLookupString())) {
          index++;
          break;
        }
      }
    }
    assertEquals(finalCount, index);
  }

  protected void assertStringItems(String... items) {
    assertOrderedEquals(getLookupStrings(new ArrayList<>()), items);
  }

  protected void assertContainsItems(final String... expected) {
    final Set<String> actual = getLookupStrings(new HashSet<>());
    for (String s : expected) {
      assertTrue("Expected '" + s + "' not found in " + actual,
                 actual.contains(s));
    }
  }

  protected void assertNotContainItems(final String... unexpected) {
    final Set<String> actual = getLookupStrings(new HashSet<>());
    for (String s : unexpected) {
      assertFalse("Unexpected '" + s + "' presented in " + actual,
                  actual.contains(s));
    }
  }

  private <T extends Collection<String>> T getLookupStrings(T actual) {
    if (myItems != null) {
      for (LookupElement lookupElement : myItems) {
        actual.add(lookupElement.getLookupString());
      }
    }
    return actual;
  }

  protected LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(getEditor());
  }
}