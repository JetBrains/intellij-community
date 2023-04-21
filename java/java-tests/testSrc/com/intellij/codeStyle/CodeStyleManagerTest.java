// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeStyle;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.assertions.Assertions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CodeStyleManagerTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSuggestSemanticNameEnumConstantVariable() {
    testSuggestedFirstName("""
                             public class Test {
                                 public static void main(String[] args) {
                                     check("Some", (Month.J<caret>ANUARY), 17);
                                 }
                                 
                                 private enum Month {
                                    JANUARY
                                 }
                             }
                             """, "january", VariableKind.LOCAL_VARIABLE);
  }

  public void testSuggestSemanticNameStringConstant() {
    testSuggestedFirstName("""
                             public class Test {
                                 public static void main(String[] args) {
                                     check("Som<caret>e", (Month.JANUARY), 17);
                                 }
                                 
                                 private enum Month {
                                    JANUARY
                                 }
                             }
                             """, "Some", VariableKind.LOCAL_VARIABLE);
  }

  public void testSuggestSemanticNameVariableNameParameter() {
    testSuggestedFirstName("""
                             public class Test {
                                 public static void main(String[] args) {
                                     Month month2 = Month.JANUARY;
                                     check("Some", m<caret>onth2, 17);
                                 }
                                 
                                 private enum Month {
                                    JANUARY
                                 }
                             }
                             """, "month2", VariableKind.PARAMETER);
  }

  public void testSuggestSemanticNameEnumConstantParameter() {
    testSuggestedFirstName("""
                             public class Test {
                                 public static void main(String[] args) {
                                     check("Some",(Month.J<caret>ANUARY), 17);
                                 }
                                 
                                 private enum Month {
                                    JANUARY
                                 }
                             }
                             """, "Month", VariableKind.PARAMETER);
  }

  private void testSuggestedFirstName(@NotNull @Language("JAVA") String text, @NotNull String expected, @NotNull VariableKind parameter) {
    PsiFile file = myFixture.configureByText("Test.java", text);
    int offset = myFixture.getCaretOffset();
    PsiElement element = PsiUtilCore.getElementAtOffset(file, offset);
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class, false);
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(getProject());
    Collection<String> names = javaCodeStyleManager.suggestSemanticNames(expression, parameter);
    Assertions.assertThat(names.iterator().next()).isEqualTo(expected);
  }
}
