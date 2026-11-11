// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.magicConstant.MagicConstantInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MagicConstantInspectionTest {
  // has to have JFrame and sources
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/magic/");
  }

  @Test
  public void testSimple() { doTest(); }
  
  @Test
  public void testManyConstantSources() { doTest(); }
  // test that the optimisation for not loading AST works
  @Test
  public void testWithLibrary() { doTest(); }
  @Test
  public void testSpecialCases() { doTest(); }
  @Test
  public void testVarargMethodCall() { doTest(); }
  @Test
  public void testEnumConstructor() { doTest(); }
  @Test
  public void testFieldInitializer() { doTest(); }
  @Test
  public void testSwitchBlock() { doTest(); }
  @Test
  public void testPropagate() { doTest(); }
  @Test
  public void testMinMax() { doTest(); }
  @Test
  public void testSwitchExpression1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_14, () -> doTest());
  }
  @Test
  public void testSwitchExpression2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_14, () -> doTest());
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");

    ReadAction.runBlocking(() -> {
      PsiClass calendarClass = myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_CALENDAR);
      assertNotNull(calendarClass, "No Calendar class in mockJDK");
      PsiElement calendarSource = calendarClass.getNavigationElement();
      assertTrue(calendarSource instanceof PsiClassImpl);
      myFixture.allowTreeAccessForFile(calendarSource.getContainingFile().getVirtualFile());
    });

    myFixture.enableInspections(new MagicConstantInspection());
    myFixture.testHighlighting(true, false, false);
  }
}
