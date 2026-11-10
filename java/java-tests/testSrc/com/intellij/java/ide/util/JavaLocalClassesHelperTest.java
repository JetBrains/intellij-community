// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.ide.util.JavaLocalClassesHelper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavaLocalClassesHelperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/local/");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testSimple() {
    doTest("$1Local");
  }

  @Test
  public void testMultipleNames() {
    doTest("$1B");
  }

  @Test
  public void testSameName() {
    doTest("$2Local");
  }

  private void doTest(String expectedName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      final PsiElement element = PsiUtilBase.getElementAtCaret(myFixture.getEditor()).getParent().getParent();

      PsiDeclarationStatement declaration = ObjectUtils.tryCast(element, PsiDeclarationStatement.class);
      PsiClass aClass = declaration == null ? null : ObjectUtils.tryCast(declaration.getDeclaredElements()[0], PsiClass.class);
      assert aClass != null && PsiUtil.isLocalClass(aClass) : "There should be local class at caret but " + element + " found";

      assertEquals(expectedName, JavaLocalClassesHelper.getName(aClass));
    });
  }
}