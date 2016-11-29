/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * @author mike
 */
public abstract class LightCompletionTestCase extends LightCodeInsightTestCase {
  protected String myPrefix;
  protected LookupElement[] myItems;
  private CompletionType myType = CompletionType.BASIC;

  @Override
  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  @Override
  protected void configureByFile(@NotNull String filePath) {
    super.configureByFile(filePath);

    complete();
  }

  protected void configureByFileNoComplete(String filePath) throws Exception {
    super.configureByFile(filePath);
  }

  protected void complete() {
    complete(1);
  }

  protected void complete(final int time) {
    new CodeCompletionHandlerBase(myType).invokeCompletion(getProject(), getEditor(), time);

    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
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
    lookup.setCurrentItem(item);
    if (completionChar == 0 || completionChar == '\n' || completionChar == '\t') {
      lookup.finishLookup(completionChar);
    } else {
      type(completionChar);
    }
  }

  protected void testByCount(int finalCount, @NonNls String... values) {
    if (myItems == null) {
      assertEquals(finalCount, 0);
      return;
    }
    int index = 0;
    for (final LookupElement myItem : myItems) {
      for (String value : values) {
        if (value == null) {
          assertFalse("Unacceptable value reached: " + myItem.getLookupString(), true);
        }
        if (value.equals(myItem.getLookupString())) {
          index++;
          break;
        }
      }
    }
    assertEquals(finalCount, index);
  }

  protected void assertStringItems(@NonNls String... items) {
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

  protected static LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }
}
