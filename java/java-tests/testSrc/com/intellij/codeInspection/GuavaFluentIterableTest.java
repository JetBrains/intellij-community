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
import com.intellij.codeInspection.java18StreamApi.GuavaFluentIterableInspection;
import com.intellij.codeInspection.java18StreamApi.StaticPseudoFunctionalStyleMethodInspection;
import com.intellij.openapi.application.PathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;
import java.util.ArrayList;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFluentIterableTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath()  {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/guava";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibraryJars("guava-17.0.jar", PathManager.getHomePath().replace(File.separatorChar, '/') + "/community/lib/",
                                 "guava-17.0.jar");
    moduleBuilder.addLibraryJars("guava-17.0.jar-2", PathManager.getHomePath().replace(File.separatorChar, '/') + "/lib/",
                                 "guava-17.0.jar");
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testBase() {
    doTest();
  }

  public void testSuitableReturnType1() {
    doTest();
  }

  public void testSuitableReturnType2() {
    doTest();
  }

  public void testSuitableReturnType3() {
    doTest();
  }

  public void testNonSuitableReturnType() {
    doTest(false);
  }

  public void testNonSuitableReturnType2() {
    doTest(false);
  }

  public void testNonSuitableMethodParameterType() {
    doTest(false);
  }

  public void testStopMethods() {
    doTest(false);
  }

  public void testLocalVariable() {
    doTest();;
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean quickFixMustBeFound) {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.enableInspections(new GuavaFluentIterableInspection());
    final QuickFixWrapper quickFix = myFixture.getAvailableIntentions()
      .stream()
      .filter(QuickFixWrapper.class::isInstance)
      .map(action -> (QuickFixWrapper)action)
      .filter(action -> action.getFix() instanceof GuavaFluentIterableInspection.ConvertGuavaFluentIterableQuickFix)
      .findFirst().orElse(null);
    if (quickFix != null) {
      myFixture.launchAction(quickFix);
    }
    assertTrue(!quickFixMustBeFound || quickFix != null);
    if (quickFixMustBeFound) {
      myFixture.checkResultByFile(getTestName(true) + "_after.java");
    }
  }
}
