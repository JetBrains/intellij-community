// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.java.refactoring.ChangeSignatureFixture;
import com.intellij.psi.PsiTypes;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.refactoring.ChangeSignatureFixtureKt.changeSignatureFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class ChangeSignatureTouchLambdaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<ChangeSignatureFixture> changeSignature =
    changeSignatureFixture(projectFixture, "/codeInsight/daemonCodeAnalyzer/lambda/changeSignature/");

  @Test
  public void testVariableDeclaration() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())}, new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testMethodArgument() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())}, new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testLambdaHierarchy() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[0] , new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testDefaultMethodTouched() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())}, new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testDelegateInInterface() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(
      PsiTypes.booleanType()).withDefaultValue("false")}, new ThrownExceptionInfo[0], true);
  }

  @Test
  public void testAddExceptionToCatchInOneLineLambda() {
    changeSignature.get().doTest(null, null, new String[] {"java.io.IOException"}, false);
  }

  @Test
  public void testAddUncheckedExceptionInMethodRef() {
    changeSignature.get().doTest(null, null, new String[] {"java.lang.NullPointerException"}, false);
  }
}
