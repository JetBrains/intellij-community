/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
@TestDataPath("$CONTENT_ROOT/testData")
public abstract class CompletionSortingTestCase extends LightFixtureCompletionTestCase {
  private final CompletionType myType;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  protected CompletionSortingTestCase(CompletionType type) {
    myType = type;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      LookupManager.getInstance(getProject()).hideActiveLookup();
      UISettings.getInstance().setSortLookupElementsLexicographically(false);
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected abstract String getBasePath();

  protected void checkPreferredItems(final int selected, @NonNls final String... expected) {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(selected, expected);
  }

  protected void assertPreferredItems(final int selected, @NonNls final String... expected) {
    myFixture.assertPreferredCompletionItems(selected, expected);
  }

  protected LookupImpl invokeCompletion(final String path) {
    configureNoCompletion(path);
    myFixture.complete(myType);
    return getLookup();
  }

  protected void configureNoCompletion(String path) {
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(path, com.intellij.openapi.util.text.StringUtil.getShortName(path, '/')));
  }

  protected static void incUseCount(final LookupImpl lookup, final int index) {
    imitateItemSelection(lookup, index);
    refreshSorting(lookup);
  }

  protected static void refreshSorting(final LookupImpl lookup) {
    lookup.setSelectionTouched(false);
    lookup.resort(true);
  }

  protected static void imitateItemSelection(final LookupImpl lookup, final int index) {
    final LookupElement item = lookup.getItems().get(index);
    lookup.setCurrentItem(item);
    CompletionLookupArranger.collectStatisticChanges(item, lookup);
    CompletionLookupArranger.applyLastCompletionStatisticsUpdate();
  }
}
