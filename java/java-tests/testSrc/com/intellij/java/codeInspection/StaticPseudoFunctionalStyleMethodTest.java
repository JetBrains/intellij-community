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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.java18StreamApi.StaticPseudoFunctionalStyleMethodInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

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
    moduleBuilder.addLibrary("guava", ArrayUtil.toStringArray(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("Guava")));
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
    myFixture.launchAction(action);
    myFixture.checkResultByFile(getTestName(true) + "/test_after.java");
  }
}
