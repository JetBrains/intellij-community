/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
    LookupManager.getInstance(getProject()).hideActiveLookup();
    UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = false;
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    super.tearDown();
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
