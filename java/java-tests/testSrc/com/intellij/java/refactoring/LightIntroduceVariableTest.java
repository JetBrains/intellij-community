// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LightIntroduceVariableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  @Test
  public void testExpressionsUnderCaret() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, "package a; class A {{new Double(0.<caret>)}}");
    ReadAction.runBlocking(() -> {
      List<PsiExpression> expressions =
        CommonJavaRefactoringUtil.collectExpressions(file, myFixture.getEditor(), myFixture.getCaretOffset(), false);
      assertThat(expressions).hasSize(2);
    });
  }
  
  @Test
  public void testPreferVarargsToBoxing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, "class A { void m(int... is) {} {m(nu<caret>ll);}}");
    ReadAction.runBlocking(() -> {
      PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiExpression.class);
      assertNotNull(expression);
      PsiType type = CommonJavaRefactoringUtil.getTypeByExpression(expression);
      assertTrue(type instanceof PsiArrayType);
    });
  }

  @Test
  public void testWithDefaultNullability() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.checkerframework.framework.qual; public class DefaultQualifier {}");
    myFixture.addClass("package org.checkerframework.checker.nullness.qual; @java.lang.annotation.Target({ElementType.TYPE_USE}) public class NonNull {}");
    myFixture.addClass("package org.checkerframework.checker.nullness.qual; @java.lang.annotation.Target({ElementType.TYPE_USE}) public @interface Nullable {}");
    myFixture.addFileToProject("refactoring/introduceVariable/package-info.java", """
      @org.checkerframework.framework.qual.DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.ALL)
      package refactoring.introduceVariable;
      import org.checkerframework.checker.nullness.qual.NonNull;
      """);
    MockIntroduceVariableHandler handler = new MockIntroduceVariableHandler("m", false, false, false, 
                                                                            "java.lang.@org.checkerframework.checker.nullness.qual.Nullable String");
    String baseName = "/refactoring/introduceVariable/" + myFixture.getTestName(false);
    myFixture.configureByFile(baseName + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(baseName + ".after.java");
    });
  }

}
