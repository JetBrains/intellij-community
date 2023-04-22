// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NormalSwitchCompletionVariantsTest extends LightFixtureCompletionTestCase {
  private static final String[] COMMON_VARIANTS = {"case", "default"};
  private static final String[] COMMON_OBJECT_VARIANTS = ArrayUtil.mergeArrays(COMMON_VARIANTS, "case null", "case default");

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/variants/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public void testCompletionPrimitiveTypeExpr() { doTest(COMMON_VARIANTS); }
  public void testCompletionPrimitiveTypeStmt() { doTest(COMMON_VARIANTS); }
  public void testCompletionVariantsInStmt() { doTest(COMMON_OBJECT_VARIANTS); }
  public void testCompletionVariantsInExpr() { doTest(COMMON_OBJECT_VARIANTS); }

  @NeedsIndex.Full
  public void testCompletionSealedHierarchyStmt() {
    doTest(ArrayUtil.mergeArrays(COMMON_OBJECT_VARIANTS, "case Variant1", "case Variant2"));
  }

  @NeedsIndex.Full
  public void testCompletionSealedHierarchyExpr() {
    doTest(ArrayUtil.mergeArrays(COMMON_OBJECT_VARIANTS, "case Variant1", "case Variant2"));
  }

  private void doTest(String[] variants) {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC);

    final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertSameElements(lookupElementStrings, variants);
  }
}
