/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.junit.Assert;

import java.util.Arrays;

public class NameSuggestionsByExpressionTest extends LightCodeInsightFixtureTestCase {
  public void testNameSuggestionFromLiteralArgument() {
    PsiFile file = myFixture.configureByText("A.java", "class A {{new Str<caret>ing(\"string with spaces\")}}");
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    SuggestedNameInfo nameInfo = JavaCodeStyleManager.getInstance(getProject())
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, null);
    Assert.assertArrayEquals("Suggested: " + Arrays.toString(nameInfo.names), 
                             new String[] {"string_with_spaces", "stringWithSpaces", "with_spaces", "withSpaces", "spaces", "string", "s"}, 
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
