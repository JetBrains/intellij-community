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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ContractInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ContractCheckTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/contractCheck/";
  }

  private void doTest() {
    myFixture.enableInspections(new ContractInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testTrueInsteadOfFalse() { doTest(); }
  public void testTrueInsteadOfFail() { doTest(); }
  public void testWrongFail() { doTest(); }
  public void testNotNullStringLiteral() { doTest(); }
  public void testPlainDelegation() { doTest(); }
  public void testDelegationToInstanceMethod() { doTest(); }
  public void testFailDelegation() { doTest(); }
  public void testDelegationWithUnknownArgument() { doTest(); }
  public void testEqualsUnknownValue() { doTest(); }
  public void testMissingFail() { doTest(); }
  public void testExceptionWhenDeclaredNotNull() { doTest(); }
  public void testCheckSuperContract() { doTest(); }
  public void testNestedCallsMayThrow() { doTest(); }

  public void testSignatureIssues() { doTest(); }
  public void testVarargInferred() { doTest(); }
  public void testDoubleParameter() { doTest(); }
  public void testReturnPrimitiveArray() { doTest(); }
  public void testCheckConstructorContracts() { doTest(); }

  public void testPassingVarargsToDelegate() { doTest(); }
  public void testUnknownIfCondition() { doTest(); }
}
