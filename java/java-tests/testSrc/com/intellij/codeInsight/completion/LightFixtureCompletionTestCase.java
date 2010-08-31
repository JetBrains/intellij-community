package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public abstract class LightFixtureCompletionTestCase extends LightCodeInsightFixtureTestCase {
  protected LookupElement[] myItems;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).clearStatistics();
  }


  @Override
  protected void tearDown() throws Exception {
    myItems = null;
    super.tearDown();
  }

  protected void configureByFile(String path) {
    myFixture.configureByFile(path);
    complete();
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

  protected void selectItem(LookupElement item, char completionChar) {
    final LookupImpl lookup = getLookup();
    lookup.setCurrentItem(item);
    if (completionChar == 0 || completionChar == '\n' || completionChar == '\t') {
      lookup.finishLookup(completionChar);
    } else {
      type(completionChar);
    }
  }

  protected LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
  }

  protected void assertStringItems(String... items) {
    assertOrderedEquals(myFixture.getLookupElementStrings(), items);
  }

  protected void type(String s) {
    myFixture.type(s);
  }
  protected void type(char c) {
    myFixture.type(c);
  }
}
