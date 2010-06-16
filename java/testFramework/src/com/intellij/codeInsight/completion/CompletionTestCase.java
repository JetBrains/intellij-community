package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;

import java.util.Arrays;

/**
 * @author mike
 */
public abstract class CompletionTestCase extends DaemonAnalyzerTestCase {
  protected String myPrefix;
  protected LookupElement[] myItems;
  private CompletionType myType = CompletionType.BASIC;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).clearStatistics();
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(myProject).hideActiveLookup();
    super.tearDown();
    myItems = null;
  }

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
    new CodeCompletionHandlerBase(myType).invokeCompletion(myProject, myEditor, myFile, time);

    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup == null ? null : lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    myPrefix = lookup == null ? "" : lookup.getItems().get(0).getPrefixMatcher().getPrefix();
  }

  public void setType(CompletionType type) {
    myType = type;
  }

  protected void selectItem(LookupElement item, char ch) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(myProject).getActiveLookup();
    lookup.setCurrentItem(item);
    lookup.finishLookup(ch);
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }

  protected void testByCount(int finalCount, String... values) {
    int index = 0;
    if (myItems == null) {
      assertEquals(0, finalCount);
      return;
    }
    for (int i = 0; i < myItems.length; i++) {
      final LookupElement myItem = myItems[i];
      for (int j = 0; j < values.length; j++) {
        if (values[j] == null) {
          assertFalse("Unacceptable value reached", true);
        }
        if (values[j].equals(myItem.getLookupString())) {
          index++;
          break;
        }
      }
    }
    assertEquals(Arrays.toString(myItems), finalCount, index);
  }

  protected LookupImpl getActiveLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }
}
