// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Assert;

import java.util.Arrays;

public class NameSuggestionsByExpressionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNameSuggestionFromLiteralArgument() {
    PsiFile file = myFixture.configureByText("A.java", "class A {{new Str<caret>ing(\"string with spaces\")}}");
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    SuggestedNameInfo nameInfo = JavaCodeStyleManager.getInstance(getProject())
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, null);
    Assert.assertArrayEquals("Suggested: " + Arrays.toString(nameInfo.names), 
                             new String[] {"stringWithSpaces", "string_with_spaces", "withSpaces", "with_spaces", "spaces", "string", "s"}, 
                             nameInfo.names);
  }

  public void testWordByPreposition() {
    PsiFile file = myFixture.configureByText("A.java", "class A {{getParent<caret>OfType()} String getParentOfType() {return null;}}");
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    SuggestedNameInfo nameInfo = JavaCodeStyleManager.getInstance(getProject())
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, null);
    Assert.assertArrayEquals("Suggested: " + Arrays.toString(nameInfo.names), 
                             new String[] {"getParentOfType", "parentOfType", "ofType", "type", "parent", "s"}, 
                             nameInfo.names);
  }

  public void testNameByAssignmentContext() {
    PsiFile file = myFixture.configureByText("A.java", "class A {{String bar = \"<caret>\";}}");
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    SuggestedNameInfo nameInfo = JavaCodeStyleManager.getInstance(getProject())
      .suggestVariableName(VariableKind.PARAMETER, null, expression, null);
    Assert.assertArrayEquals("Suggested: " + Arrays.toString(nameInfo.names), 
                             new String[] {"bar", "s"}, 
                             nameInfo.names);
  }
}
