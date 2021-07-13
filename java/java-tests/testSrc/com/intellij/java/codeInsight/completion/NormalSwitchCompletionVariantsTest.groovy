// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class NormalSwitchCompletionVariantsTest extends LightFixtureCompletionTestCase {
  private static final String[] VARIANTS = ["case", "case null", "default"]

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/variants/"
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17
  }

  void testCompletionVariantsInStmt() { doTest() }
  void testCompletionVariantsInExpr() { doTest() }

  void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC)

    final List<String> lookupElementStrings = myFixture.getLookupElementStrings()
    assertSameElements(lookupElementStrings, VARIANTS)
  }
}