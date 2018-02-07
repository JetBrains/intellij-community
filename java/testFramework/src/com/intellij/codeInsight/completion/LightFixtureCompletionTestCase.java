/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class LightFixtureCompletionTestCase extends LightCodeInsightFixtureTestCase {
  protected LookupElement[] myItems;

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_6;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myItems = null;
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    }
    finally {
      super.tearDown();
    }
  }

  protected void configureByFile(String path) {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
    complete();
  }

  protected void configureByTestName() {
    configureByFile("/" + getTestName(false) + ".java");
  }

  protected void doAntiTest() {
    configureByTestName();
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  protected void complete() {
    myItems = myFixture.completeBasic();
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }

  protected void checkResultByFile(String path) {
    myFixture.checkResultByFile(path);
  }

  protected void selectItem(@NotNull LookupElement item, final char completionChar) {
    final LookupImpl lookup = getLookup();
    lookup.setCurrentItem(item);
    if (LookupEvent.isSpecialCompletionChar(completionChar)) {
      lookup.finishLookup(completionChar);
    } else {
      type(completionChar);
    }
  }

  protected LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
  }

  protected void assertFirstStringItems(String... items) {
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertOrderedEquals(strings.subList(0, Math.min(items.length, strings.size())), items);
  }
  protected void assertStringItems(String... items) {
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertOrderedEquals(strings, items);
  }

  protected void type(String s) {
    myFixture.type(s);
  }
  protected void type(char c) {
    myFixture.type(c);
  }
}
