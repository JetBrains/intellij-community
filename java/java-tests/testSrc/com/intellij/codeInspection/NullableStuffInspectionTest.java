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
  public void testNotNullCustomException() throws Exception{ doTest(); }
  public void testNotNullFieldNotInitialized() throws Exception{ doTest(); }

  public void testGetterSetterProblems() throws Exception{ doTest(); }
  public void testOverriddenMethods() throws Exception{
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  public void testHonorSuperParameterDefault() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.addClass("import javax.annotation.*; package foo; public interface NullableFunction { void fun(@Nullable Object o); }");
    myFixture.addClass("package foo; public interface AnyFunction { void fun(Object o); }");

    doTest();
  }

  public void testHonorThisParameterDefault() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHonorParameterDefaultInSetters() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

}