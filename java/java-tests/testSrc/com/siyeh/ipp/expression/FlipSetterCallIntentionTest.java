// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see FlipSetterCallIntention
 */
@TestApplication
public class FlipSetterCallIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "expression/flip_setter_call", IntentionPowerPackBundle.message("flip.setter.call.intention.family.name"));

  @Test
  public void testSimple() { ipp.get().doTest(); }
  @Test
  public void testUnqualified() { ipp.get().doTest(); }
  @Test
  public void testParentheses() { ipp.get().doTest(); }
  @Test
  public void testSelection() { ipp.get().doTest(); }
}
