// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class LightFixtureCompletionTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected LookupElement[] myItems;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
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
      CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
      if (codeInsightSettings != null) {
        codeInsightSettings.setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
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
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    PlatformTestUtil.waitForAllDocumentsCommitted(10, TimeUnit.SECONDS);
  }

  protected LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
  }

  protected void assertFirstStringItems(String... items) {
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertOrderedEquals(ContainerUtil.getFirstItems(strings, items.length), items);
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
