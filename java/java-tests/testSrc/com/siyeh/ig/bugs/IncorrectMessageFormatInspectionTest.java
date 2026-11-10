// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.psiutils.MethodMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class IncorrectMessageFormatInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/bugs/incorrect_message_format");
  }

  private void doTest(List<String> classes, List<String> methods) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IncorrectMessageFormatInspection inspection = new IncorrectMessageFormatInspection();
    MethodMatcher matcher = inspection.myMethodMatcher;
    for (int i = 0; i < classes.size(); i++) {
      matcher.add(classes.get(i), methods.get(i));
    }
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testIncorrectMessageFormat() {
    doTest(List.of(), List.of());
  }

  @Test
  public void testIncorrectMessageFormatWithCustomMethods() {
    doTest(List.of("org.example.util.Formatter"), List.of("formatNew"));
  }
}
