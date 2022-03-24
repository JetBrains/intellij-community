// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.java.refactoring.ChangeSignatureBaseTest;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;

public class ChangeSignatureTouchLambdaTest extends ChangeSignatureBaseTest {
 
  public void testVariableDeclaration() {
    doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testMethodArgument() {
    doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testLambdaHierarchy() {
    doTest(null, null, null, new ParameterInfoImpl[0] , new ThrownExceptionInfo[0], false);
  }

  public void testDefaultMethodTouched() {
    doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testDelegateInInterface() {
    doTest(null, null, null, new ParameterInfoImpl[] {ParameterInfoImpl.createNew().withName("b").withType(PsiType.BOOLEAN).withDefaultValue("false")}, new ThrownExceptionInfo[0], true);
  }

  public void testAddExceptionToCatchInOneLineLambda() {
    doTest(null, null, new String[] {"java.io.IOException"}, false);
  }

  public void testAddUncheckedExceptionInMethodRef() {
    doTest(null, null, new String[] {"java.lang.NullPointerException"}, false);
  }

  @Override
  protected String getRelativePath() {
    return "/codeInsight/daemonCodeAnalyzer/lambda/changeSignature/";
  }
}
