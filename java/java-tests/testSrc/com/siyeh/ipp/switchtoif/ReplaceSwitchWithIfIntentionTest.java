// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.switchtoif;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiKeyword;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ReplaceSwitchWithIfIntentionTest extends IPPTestCase {

  public void testReplaceInt() {
    doTest();
  }

  public void testReplaceInteger() {
    doTest();
  }

  public void testReplaceChar() {
    doTest();
  }

  public void testReplaceCharacter() {
    doTest();
  }

  public void testDefaultOnly() {
    assertIntentionNotAvailable();
  }

  public void testReplaceRecordPattern() {
    doTest();
  }

  public void testReplaceEnum() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, () -> {
      doTest();
    });
  }

  public void testReplaceWithBoolean1() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doTest();
    });
  }

  public void testReplaceWithBoolean2() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doTest();
    });
  }

  public void testReplaceWithBoolean3() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doTest();
    });
  }

  public void testReplaceWithBoolean4() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doTest();
    });
  }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.SWITCH, PsiKeyword.IF);
  }

  @Override
  protected String getRelativePath() {
    return "switchtoif/replaceSwitchToIf";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_19;
  }
}
