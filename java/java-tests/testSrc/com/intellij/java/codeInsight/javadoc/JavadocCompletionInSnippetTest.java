// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestIndexingModeSupporter;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.IndexingModeCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_17;

public class JavadocCompletionInSnippetTest extends BasePlatformTestCase implements TestIndexingModeSupporter {
  private @NotNull IndexingMode myIndexingMode = IndexingMode.SMART;

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for overriding method completion)")
  public void testOverrideMethod() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("before" + getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/codeInsight/javadoc/snippet/completion/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  @Override
  public void setIndexingMode(@NotNull IndexingMode mode) {
    myIndexingMode = mode;
  }

  @Override
  public @NotNull IndexingMode getIndexingMode() {
    return myIndexingMode;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture = IndexingModeCodeInsightTestFixture.Companion.wrapFixture(myFixture, getIndexingMode());
    myIndexingMode.setUpTest(getProject(), getTestRootDisposable());
  }
}
