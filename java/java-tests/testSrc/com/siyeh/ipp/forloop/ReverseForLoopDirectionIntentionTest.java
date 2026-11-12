// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.forloop;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReverseForLoopDirectionIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "forloop/reverse", IntentionPowerPackBundle.message("reverse.for.loop.direction.intention.name"));

  @Test
  public void testAssignmentUpdate() { ipp.get().doTest(); }
  @Test
  public void testOperatorAssignment() { ipp.get().doTest(); }
  @Test
  public void testComment() { ipp.get().doTest(); }
}