/*
 * User: anna
 * Date: 19-Apr-2010
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class RedundantTypeArgsInspectionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantTypeArgs/";
  }

  private void doTest() throws Throwable {
    final RedundantTypeArgsInspection inspection = new RedundantTypeArgsInspection();
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testReturnPrimitiveTypes() throws Throwable { // javac non-boxing: IDEA-53984
    doTest();
  }
}