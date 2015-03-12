/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.java18StreamApi.StaticPseudoFunctionalStyleMethodInspection;
import com.intellij.openapi.application.PathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/lambdaLibsStatic";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibraryJars("guava-17.0.jar", PathManager.getHomePath().replace(File.separatorChar, '/') + "/community/lib/",
                                 "guava-17.0.jar");
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void _testSimpleTransform() {
    doTest();
  }

  public void _testSimpleFilter() {
    doTest();
  }

  public void _testSimpleFind() {
    doTest();
  }

  public void _testSimpleAll() {
    doTest();
  }

  public void _testSimpleAny() {
    doTest();
  }

  public void _testLambdaIsntAnonymous() {
    doTest();
  }

  public void _testLambdaIsntAnonymous2() {
    doTest();
  }

  public void _testLambdaIsntAnonymous3() {
    doTest();
  }

  public void _testReplaceWithMethodReference() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + "/test.java");
    myFixture.enableInspections(new StaticPseudoFunctionalStyleMethodInspection());
    boolean isQuickFixFound = false;
    for (IntentionAction action : myFixture.getAvailableIntentions()) {
      if (action instanceof QuickFixWrapper) {
        final LocalQuickFix fix = ((QuickFixWrapper)action).getFix();
        if (fix instanceof StaticPseudoFunctionalStyleMethodInspection.ReplacePseudoLambdaWithLambda) {
          myFixture.launchAction(action);
          isQuickFixFound = true;
          break;
        }
      }
    }
    assertTrue(isQuickFixFound);
    myFixture.checkResultByFile(getTestName(true) + "/test_after.java");
  }
}
