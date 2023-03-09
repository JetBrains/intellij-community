// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import org.jetbrains.annotations.NotNull;

public class RedundantCast18Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/inspection/redundantCast/lambda/";

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantCastInspection()
    };
  }

  private void doTest() {
    doTest(BASE_PATH + getTestName(false) + ".java", true, false);
  }

  public void testLambdaContext() { doTest(); }
  public void testMethodRefContext() { doTest(); }
  public void testExpectedSupertype() { doTest(); }
  public void testExpressionRendering() { doTest(); }
  public void testForeachValue() { doTest(); }
  public void testConditional() { doTest(); }
  public void testInferApplicabilityError() { doTest(); }
  public void testCastToRawType() { doTest(); }
  public void testEnumConstantArgument() { doTest(); }
  public void testCastInMultidimensionalArrayIndex() { doTest(); }
  public void testCastWithClassHierarchyWithPrivateMethods() { doTest(); }
  public void testFieldInitializer() { doTest();}
  public void testDiamondWithUpperBounds() { doTest();}
  public void testBinaryConversions() { doTest();}
  public void testInferenceIncompatibilityWithoutCast() { doTest();}
  public void testCastToPrimitive() { doTest();}
  public void testParenthesisAroundConditional() { doTest();}
  public void testMiscStatements() { doTest();}
  public void testSameSubstitutor() { doTest();}
  public void testSameUpperBounds() { doTest();}
  public void testSameResolveWithConditionalBranches() { doTest();}
  public void testIgnoreMessageFormatCall() { doTest();}
}