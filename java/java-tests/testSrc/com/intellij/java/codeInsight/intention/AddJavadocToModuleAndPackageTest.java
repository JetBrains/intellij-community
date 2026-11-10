// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class AddJavadocToModuleAndPackageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testPackageInfo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("package-info.java", "package org.some.awe<caret>some;");
    myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
    myFixture.checkResult("""
                            /**
                             *\s
                             */
                            package org.some.awesome;""");
  }

  @Test
  public void testModuleInfo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("module-info.java", "module org.some.awe<caret>some{}");
    myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
    myFixture.checkResult("""
                            /**
                             *\s
                             */
                            module org.some.awesome{}""");
  }

  @Test
  public void testPackageInfoMarkdown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("package-info.java", "package org.some.awe<caret>some;");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_23, ()-> {
      myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
      myFixture.checkResult("""
                              /// <caret>
                              package org.some.awesome;""");
    });
  }

  @Test
  public void testModuleInfoMarkdown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("module-info.java", "module org.some.awe<caret>some{}");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_23, ()-> {
        myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
        myFixture.checkResult("""
                            /// <caret>
                            module org.some.awesome{}""");
      });
  }
}
