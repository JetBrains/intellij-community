// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class MakeCallChainIntoCallSequenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "concatenation/call_chain", IntentionPowerPackBundle.message("make.call.chain.into.call.sequence.intention.name"));

  @Test
  public void testBuilder() { ipp.get().doTest(); }
  @Test
  public void testBuilderIntoFinal() { ipp.get().doTest(); }
  @Test
  public void testBuilderStatic() { ipp.get().doTest(); }
  @Test
  public void testBuilderStaticUnqualified() { ipp.get().doTest(); }
  @Test
  public void testBuilderField() { ipp.get().doTest(); }
  @Test
  public void testBuilderFieldString() { ipp.get().doTest(); }
  @Test
  public void testBuilderLambda() { ipp.get().doTest(); }
  @Test
  public void testBuilderLambdaVoid() { ipp.get().doTest(); }
  @Test
  public void testBuilderReturn() { ipp.get().doTest(); }
  @Test
  public void testBuilderReturnString() { ipp.get().doTest(); }
  @Test
  public void testBuilderCompoundAssignment() { ipp.get().doTest(); }
  @Test
  public void testBuilderInIf() { ipp.get().doTest(); }
  @Test
  public void testBuilderSwitchRuleExpression() { ipp.get().doTest(); }
  @Test
  public void testBuilderNewExpression() { ipp.get().doTest(); }
  @Test
  public void testVarNamedX() { ipp.get().doTest(); }
  @Test
  public void testTransformation() { ipp.get().assertIntentionNotAvailable();}
  //should be probably possible?
  @Test
  public void testThisCollapse() { ipp.get().assertIntentionNotAvailable();}

}