// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection16Test extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testInstanceOfPattern() { doTest(); }
  public void testSwitchStatements() { doTest(); }
  public void testSwitchStatementUnreachableBranches() { doTest(); }
  public void testSwitchExpressions() { doTest(); }
  public void testSwitchExpressionsNullability() { doTest(); }
  public void testConstantDescAsWrapperSupertype() {
    myFixture.addClass("package java.lang.constant; public interface ConstantDesc {}");
    doTest();
  }
  public void testSwitchExpressionAndLambdaInlining() { doTest(); }
  public void testRecordAccessorStability() { doTest(); }
  public void testSealedClassCast() { doTest(); }
  public void testCastToSealedInterface() { doTest(); }
  public void testRecordAccessorContainerAnnotation() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, METHOD}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NonnullByDefault {}");
    doTest(); 
  }
  public void testStaticFieldInAnonymous() { doTest(); }

  public void testMutabilityJdk16() { doTest(); }
  
  public void testAccessorNullityUnderDefaultQualifier() {
    addCheckerAnnotations(myFixture);
    doTest();
  }
}