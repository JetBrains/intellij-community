// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class UnsupportedVersionHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testPatternInSwitch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //noinspection UsagesOfObsoleteApi
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_17_PREVIEW, () -> {
      doTestPatternInSwitch();
    });
  }

  private void doTestPatternInSwitch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      class X {
        void test(Object obj) {
          switch(obj) {
            // Simple patterns are supported due to aliasing
            case String s -> System.out.println("hello");
            // guards with && are not supported anymore
            case Integer i<error descr="':' or '->' expected"> </error><error descr="Unexpected token">&&</error> <error descr="Not a statement">i > 0</error> <error descr="Unexpected token">-></error> System.out.println("oops");
            default -> {}
          }
        }
      }
      """);
    myFixture.checkHighlighting();
    JavaParameters javaParameters = new JavaParameters();
    try {
      javaParameters.configureByModule(myFixture.getModule(), JavaParameters.CLASSES_ONLY);
    }
    catch (CantRunException e) {
      throw new RuntimeException(e);
    }
    assertEquals("--enable-preview", javaParameters.getVMParametersList().getParametersString());
  }
}
