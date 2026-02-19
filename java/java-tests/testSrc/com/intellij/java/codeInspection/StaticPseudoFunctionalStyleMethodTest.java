// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.java18StreamApi.StaticPseudoFunctionalStyleMethodInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/lambdaLibsStatic";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibrary("guava", ArrayUtilRt.toStringArray(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("Guava")));
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testSimpleTransform() {
    doTest();
  }

  public void testSimpleFilter() {
    doTest();
  }

  public void testSimpleFind() {
    doTest();
  }

  public void testSimpleAll() {
    doTest();
  }

  public void testSimpleAny() {
    doTest();
  }

  public void testLambdaIsntAnonymous() {
    doTest();
  }

  public void testLambdaIsntAnonymous2() {
    doTest();
  }

  public void testLambdaIsntAnonymous3() {
    doTest();
  }

  public void testFindWithDefaultValue() {
    doTest();
  }

  public void testFilterWithInstanceOf() {
    doTest();
  }

  public void testDiamondResolve() {
    doTest();
  }

  public void testTransformLambda() {
    doTest();
  }

  public void testTransformMethodCalling() {
    doTest();
  }

  public void testTransformMethodRef() {
    doTest();
  }

  public void testLambdaIsVariable() {
    doTest();
  }

  public void testListsTransform() {
    doTest();
  }

  public void _testReplaceWithMethodReference() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + "/test.java");
    myFixture.enableInspections(new StaticPseudoFunctionalStyleMethodInspection());
    IntentionAction action = myFixture.getAvailableIntention("Replace with Java Stream API pipeline");
    assertNotNull("Quick fix isn't found", action);
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResultByFile(getTestName(true) + "/test_after.java");
  }
}
