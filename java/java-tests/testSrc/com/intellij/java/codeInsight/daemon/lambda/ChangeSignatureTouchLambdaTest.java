/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.java.refactoring.ChangeSignatureBaseTest;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;

public class ChangeSignatureTouchLambdaTest extends ChangeSignatureBaseTest {
 
  public void testVariableDeclaration() {
    doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testMethodArgument() {
    doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testDefaultMethodTouched() {
    doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testDelegateInInterface() {
    doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "false")}, new ThrownExceptionInfo[0], true);
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
