/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 08.07.2006
 * Time: 0:07:45
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class NullableStuffInspectionTest extends LightCodeInsightFixtureTestCase {
  private final NullableStuffInspection myInspection = new NullableStuffInspection();
  {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/nullableProblems/";
  }

  private void doTest() {
    myFixture.enableInspections(myInspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testProblems() throws Exception{ doTest(); }
  public void testProblems2() throws Exception{ doTest(); }
  public void testNullableFieldNotnullParam() throws Exception{ doTest(); }
  public void testNotNullFieldNullableParam() throws Exception{ doTest(); }

  public void testGetterSetterProblems() throws Exception{ doTest(); }
  public void testOverriddenMethods() throws Exception{
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

}