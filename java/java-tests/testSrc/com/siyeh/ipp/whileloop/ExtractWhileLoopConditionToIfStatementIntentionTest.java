// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.whileloop;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ExtractWhileLoopConditionToIfStatementIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class ExtractWhileLoopConditionToIfStatementIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "whileloop/extract_while_loop_condition_to_if_statement", IntentionPowerPackBundle.message("extract.while.loop.condition.to.if.statement.intention.name"));

  @Test
  public void testSimple() { ipp.get().doTest(); }
  @Test
  public void testNoBody() { ipp.get().doTest(); }
  @Test
  public void testTrue() { ipp.get().assertIntentionNotAvailable(); }
}