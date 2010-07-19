/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
@TestDataPath("$CONTENT_ROOT/testData")
public abstract class CompletionSortingTestCase extends LightCompletionTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  protected CompletionSortingTestCase(CompletionType type) {
    setType(type);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }


  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  protected abstract String getBasePath();

  protected void checkPreferredItems(final int selected, @NonNls final String... expected) throws Exception {
    invokeCompletion(getBasePath() + "/" + getTestName(false) + ".java");
    assertPreferredItems(selected, expected);
  }

  protected static void assertPreferredItems(final int selected, @NonNls final String... expected) {
    final LookupImpl lookup = getLookup();
    final JList list = lookup.getList();
    final List<LookupElement> model = lookup.getItems();
    final List<String> actual = new ArrayList<String>();
    final int count = lookup.getPreferredItemsCount();
    for (int i = 0; i < count; i++) {
      actual.add(model.get(i).getLookupString());
    }
    if (!actual.equals(Arrays.asList(expected))) {
      final List<String> strings = new ArrayList<String>();
      for (int i = 0; i < model.size(); i++) {
        final LookupElement item = model.get(i);
        strings.add(item.getLookupString() + Arrays.toString(item.getUserData(CompletionLookupArranger.WEIGHT)));
        if (i == count - 1) {
          strings.add("---");
        }
      }
      assertOrderedEquals(strings, expected);
    }
    assertEquals(selected, list.getSelectedIndex());
  }

  protected LookupImpl invokeCompletion(final String filePath) throws Exception {
    configureByFile(filePath);
    return getLookup();
  }

  protected static void incUseCount(final LookupImpl lookup, final int index) {
    imitateItemSelection(lookup, index);
    refreshSorting(lookup);
  }

  protected static void refreshSorting(final LookupImpl lookup) {
    for (final LookupElement item : lookup.getItems()) {
      item.putUserData(CompletionLookupArranger.WEIGHT, null);
      item.putUserData(CompletionLookupArranger.RELEVANCE_KEY, null);
    }
    lookup.resort();
  }

  protected static void imitateItemSelection(final LookupImpl lookup, final int index) {
    final LookupElement item = lookup.getItems().get(index);
    lookup.setCurrentItem(item);
    lookup.getLookupModel().itemSelected(item, lookup);
  }
}
