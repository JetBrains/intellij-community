// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.magicConstant.MagicConstantInspection;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MagicConstantInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/magic/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    // has to have JFrame and sources
    return JAVA_1_7_ANNOTATED;
  }

  public void testSimple() { doTest(); }
  
  public void testManyConstantSources() { doTest(); }
  // test that the optimisation for not loading AST works
  public void testWithLibrary() { doTest(); }
  public void testSpecialCases() { doTest(); }
  public void testVarargMethodCall() { doTest(); }
  public void testEnumConstructor() { doTest(); }
  public void testSwitchBlock() { doTest(); }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");

    PsiClass calendarClass = myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_CALENDAR);
    assertNotNull("No Calendar class in mockJDK", calendarClass);
    PsiElement calendarSource = calendarClass.getNavigationElement();
    assertTrue(calendarSource instanceof PsiClassImpl);
    myFixture.allowTreeAccessForFile(calendarSource.getContainingFile().getVirtualFile());

    myFixture.enableInspections(new MagicConstantInspection());
    myFixture.testHighlighting(true, false, false);
  }
}
