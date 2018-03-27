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

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author mike
 */
@PlatformTestCase.WrapInCommand
public abstract class CompletionTestCase extends DaemonAnalyzerTestCase {
  protected String myPrefix;
  protected LookupElement[] myItems;
  private CompletionType myType = CompletionType.BASIC;

  @Override
  protected void tearDown() throws Exception {
    myItems = null;
    try {
      LookupManager.getInstance(myProject).hideActiveLookup();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void configureByFile(String filePath) throws Exception {
    super.configureByFile(filePath);

    complete();
  }

  protected void configureByFileNoCompletion(String filePath) throws Exception {
    super.configureByFile(filePath);
  }

  protected void complete() {
    complete(1);
  }

  protected void complete(final int time) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    new CodeCompletionHandlerBase(myType).invokeCompletion(myProject, InjectedLanguageUtil
      .getEditorForInjectedLanguageNoCommit(myEditor, getFile()), time);

    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup == null ? null : lookup.getItems().toArray(new LookupElement[lookup.getItems().size()]);
    myPrefix = lookup == null ? "" : lookup.itemPattern(lookup.getItems().get(0));
  }

  public void setType(CompletionType type) {
    myType = type;
  }

  protected void selectItem(LookupElement item, char ch) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(myProject).getActiveLookup();
    assert lookup != null;
    lookup.setCurrentItem(item);
    lookup.finishLookup(ch);
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }

  protected void doTestByCount(int finalCount, String... values) {
    int index = 0;
    if (myItems == null) {
      assertEquals(0, finalCount);
      return;
    }
    for (final LookupElement myItem : myItems) {
      for (String value : values) {
        if (value == null) {
          assertFalse("Unacceptable value reached", true);
        }
        if (value.equals(myItem.getLookupString())) {
          index++;
          break;
        }
      }
    }
    assertEquals(Arrays.toString(myItems), finalCount, index);
  }

  @Nullable
  protected LookupImpl getActiveLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }

  protected void assertStringItems(String... strings) {
    assertNotNull(myItems);
    List<String> actual = ContainerUtil.map(myItems, element -> element.getLookupString());
    assertOrderedEquals(actual, strings);
  }
}
