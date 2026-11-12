// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * @see ReplaceForEachLoopWithIndexedForLoopIntention
 * @see ReplaceForEachLoopWithOptimizedIndexedForLoopIntention
 */
@TestApplication
public class ReplaceForEachLoopWithIndexedForLoopIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "forloop/indexed", IntentionPowerPackBundle.message("replace.for.each.loop.with.optimized.indexed.for.loop.intention.name"));

  @Test
  public void testLabeledForLoop() { ipp.get().doTest(); }
  @Test
  public void testNormalForeachLoop() { ipp.get().doTest(); }
  @Test
  public void testThisExpr() { ipp.get().doTest(); }
  @Test
  public void testNewArray() { ipp.get().doTest(); }
  @Test
  public void testNoNPE() { ipp.get().doTest(); }
  @Test
  public void testFQList() { ipp.get().doTest(); }
  @Test
  public void testBlockNeeded() { ipp.get().doTest(); }
  @Test
  public void testBlockNeededLabeled() { ipp.get().doTest(); }
  @Test
  public void testVar() { ipp.get().doTest(); }
  @Test
  public void testNoExternalAnno() { ipp.get().doTest(); }
}
