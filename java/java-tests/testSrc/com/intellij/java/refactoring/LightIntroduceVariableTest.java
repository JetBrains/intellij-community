// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LightIntroduceVariableTest extends LightJavaCodeInsightFixtureTestCase {
  public void testExpressionsUnderCaret() {
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, "package a; class A {{new Double(0.<caret>)}}");
    List<PsiExpression> expressions =
      CommonJavaRefactoringUtil.collectExpressions(file, myFixture.getEditor(), myFixture.getCaretOffset(), false);
    assertSize(2, expressions);
  }
  
  public void testPreferVarargsToBoxing() {
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, "class A { void m(int... is) {} {m(nu<caret>ll);}}");
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiExpression.class);
    assertNotNull(expression);
    PsiType type = CommonJavaRefactoringUtil.getTypeByExpression(expression);
    assertTrue(type instanceof PsiArrayType);
  }
  
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testWithDefaultNullability() {
    myFixture.addClass("package org.checkerframework.framework.qual; public class DefaultQualifier {}");
    myFixture.addClass("package org.checkerframework.checker.nullness.qual; @java.lang.annotation.Target({ElementType.TYPE_USE}) public class NonNull {}");
    myFixture.addClass("package org.checkerframework.checker.nullness.qual; @java.lang.annotation.Target({ElementType.TYPE_USE}) public @interface Nullable {}");
    myFixture.addFileToProject("p/package-info.java", "@org.checkerframework.framework.qual.DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.ALL)\n" +
                                    "package p;\n" +
                                    "import org.checkerframework.checker.nullness.qual.NonNull;\n");
    MockIntroduceVariableHandler handler = new MockIntroduceVariableHandler("m", false, false, false, 
                                                                            "java.lang.@org.checkerframework.checker.nullness.qual.Nullable String");
    String baseName = "/refactoring/introduceVariable/" + getTestName(false);
    myFixture.configureByFile(baseName + ".java");
    handler.invoke(getProject(), getEditor(), getFile(), null);
    myFixture.checkResultByFile(baseName + ".after.java");
  }

}
