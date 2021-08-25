// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class NormalSwitchCompletionVariantsTest extends LightFixtureCompletionTestCase {
  private static final String[] COMMON_VARIANTS = ["case", "default"]
  private static final String[] COMMON_OBJECT_VARIANTS = COMMON_VARIANTS + ["case null", "case default"]

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/variants/"
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17
  }

  void testCompletionPrimitiveTypeExpr() { doTest(COMMON_VARIANTS) }
  void testCompletionPrimitiveTypeStmt() { doTest(COMMON_VARIANTS) }
  void testCompletionVariantsInStmt() { doTest(COMMON_OBJECT_VARIANTS) }
  void testCompletionVariantsInExpr() { doTest(COMMON_OBJECT_VARIANTS) }

  @NeedsIndex.Full
  void testCompletionSealedHierarchyStmt() { doTest(COMMON_OBJECT_VARIANTS + ["case Variant1", "case Variant2"]) }

  @NeedsIndex.Full
  void testCompletionSealedHierarchyExpr() { doTest(COMMON_OBJECT_VARIANTS + ["case Variant1", "case Variant2"]) }

  private void doTest(String[] variants) {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC)

    final List<String> lookupElementStrings = myFixture.getLookupElementStrings()
    assertSameElements(lookupElementStrings, variants)
  }
}