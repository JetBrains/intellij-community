/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.exceptions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ConvertCatchToThrowsIntention
 */
@TestApplication
public class ConvertCatchToThrowsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "exceptions/catchToThrows", IntentionPowerPackBundle.message("convert.catch.to.throws.intention.name"));

  @Test
  public void testSingleCatch() { ipp.get().doTest(); }
  @Test
  public void testPluralCatches() { ipp.get().doTest(); }
  @Test
  public void testMultiCatch() { ipp.get().doTest(); }
  @Test
  public void testArmWithPluralCatches() { ipp.get().doTest(); }
  @Test
  public void testArmWithSingleCatch() { ipp.get().doTest(); }
  @Test
  public void testExistingThrows() { ipp.get().doTest(); }
  @Test
  public void testLambda() { ipp.get().doTest(); }
  @Test
  public void testLeaveFinallySection() { ipp.get().doTest(); }
  @Test
  public void testTryWithConflictingDeclaration() { ipp.get().doTest(); }
  @Test
  public void testInLoop() { ipp.get().doTest(); }
  @Test
  public void testInLoopSingleLine() { ipp.get().doTest(); }
  @Test
  public void testInLoopSingleLineDeclaration() { ipp.get().doTest(); }
  @Test
  public void testWithSuperMethods() {
    UiInterceptors.register(
      new ChooserInterceptor(null,
        IntentionPowerPackBundle.message("convert.catch.to.throws.super.and.current.methods")));
    ipp.get().doTest();
  }
}
