/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
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
  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  protected abstract String getBasePath();

  protected void checkPreferredItems(final int selected, @NonNls final String... expected) {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(selected, expected);
  }

  protected void assertPreferredItems(final int selected, @NonNls final String... expected) {
    myFixture.assertPreferredCompletionItems(selected, expected);
  }

  protected LookupImpl invokeCompletion(final String path) {
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(path, com.intellij.openapi.util.text.StringUtil.getShortName(path, '/')));
    myFixture.complete(myType);
    return getLookup();
  }

  protected static void incUseCount(final LookupImpl lookup, final int index) {
    imitateItemSelection(lookup, index);
    refreshSorting(lookup);
  }

  protected static void refreshSorting(final LookupImpl lookup) {
    for (final LookupElement item : lookup.getItems()) {
      item.putUserData(CompletionLookupArranger.RELEVANCE_KEY, null);
    }
    lookup.setSelectionTouched(false);
    lookup.resort();
  }

  protected static void imitateItemSelection(final LookupImpl lookup, final int index) {
    final LookupElement item = lookup.getItems().get(index);
    lookup.setCurrentItem(item);
    lookup.getArranger().itemSelected(item, lookup);
  }
}
