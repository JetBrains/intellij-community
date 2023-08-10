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
  private static final String[] COMMON_OBJECT_VARIANTS = ArrayUtil.mergeArrays(COMMON_VARIANTS, "case null", "case null, default");

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/variants/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testCompletionPrimitiveTypeExpr() { doTest(COMMON_VARIANTS); }
  public void testCompletionPrimitiveTypeStmt() { doTest(COMMON_VARIANTS); }
  public void testCompletionVariantsInStmt() { doTest(COMMON_OBJECT_VARIANTS); }
  public void testCompletionVariantsInExpr() { doTest(COMMON_OBJECT_VARIANTS); }
  public void testCompletionWhenAfterTypeTest() {
    List<String> lookup = doTestAndGetLookup();
    assertContainsElements(lookup, "when");
  }
  public void testCompletionWhenAfterDeconstruction() {
    List<String> lookup = doTestAndGetLookup();
    assertContainsElements(lookup, "when");
  }
  public void testCompletionWhenAfterPartDeconstruction() {
    List<String> lookup = doTestAndGetLookup();
    if (lookup != null) {
      assertDoesntContain(lookup, "when");
    }
    //if null - it is ok
  }

  public void testCompletionWhenAfterPartTypeTest() {
    List<String> lookup = doTestAndGetLookup();
    if (lookup != null) {
      assertDoesntContain(lookup, "when");
    }
    //if null - it is ok
  }

  @NeedsIndex.Full
  public void testCompletionSealedHierarchyStmt() {
    doTest(ArrayUtil.mergeArrays(COMMON_OBJECT_VARIANTS, "case Variant1", "case Variant2"));
  }

  @NeedsIndex.Full
  public void testCompletionSealedHierarchyExpr() {
    doTest(ArrayUtil.mergeArrays(COMMON_OBJECT_VARIANTS, "case Variant1", "case Variant2"));
  }

  private void doTest(String[] variants) {
    final List<String> lookupElementStrings =doTestAndGetLookup();
    assertSameElements(lookupElementStrings, variants);
  }
  private List<String> doTestAndGetLookup() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC);

    return myFixture.getLookupElementStrings();
  }
}
