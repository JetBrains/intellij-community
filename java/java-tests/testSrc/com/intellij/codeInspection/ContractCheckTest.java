package com.intellij.codeInspection;

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

  public void testPassingVarargsToDelegate() { doTest(); }
}
