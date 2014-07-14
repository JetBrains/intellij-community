/*
 * User: anna
 * Date: 19-Apr-2010
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class RedundantTypeArgsInspectionTest extends LightDaemonAnalyzerTestCase {

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] { new RedundantTypeArgsInspection()};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Throwable {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), myTestRootDisposable);
    doTest("/inspection/redundantTypeArgs/" + getTestName(false) + ".java", true, false);
  }

  public void testReturnPrimitiveTypes() throws Throwable { // javac non-boxing: IDEA-53984
    doTest();
  }

  public void testConditionalExpression() throws Throwable {
    doTest();
  }

  public void testBoundInference() throws Throwable {
    doTest();
  }

  public void testNestedCalls() throws Throwable {
    doTest();
  }
}