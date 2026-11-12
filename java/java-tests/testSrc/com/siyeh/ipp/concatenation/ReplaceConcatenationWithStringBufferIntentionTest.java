// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ReplaceConcatenationWithStringBufferIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceConcatenationWithStringBufferIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "concatenation/string_builder", IntentionPowerPackBundle.message("replace.concatenation.with.string.builder.intention.name"));

  @Test
  public void testNonStringConcatenationStart() { ipp.get().doTest(); }
  @Test
  public void testConcatenationInsideAppend() { ipp.get().doTest(); }
  @Test
  public void testConstantRequiredInsideAnnotationMethod() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testConstantRequiredInSwitchCaseElement() { ipp.get().assertIntentionNotAvailable(); }
}
