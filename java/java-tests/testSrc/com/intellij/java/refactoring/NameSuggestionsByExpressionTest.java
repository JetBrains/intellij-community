// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Assert;

import java.util.Arrays;

public class NameSuggestionsByExpressionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNameSuggestionFromLiteralArgument() {
    checkSuggestions("class A {{new Str<caret>ing(\"string with spaces\")}}",
                     VariableKind.LOCAL_VARIABLE,
                     "stringWithSpaces", "string_with_spaces", "withSpaces", "with_spaces", "spaces", "string", "s");
  }

  public void testWordByPreposition() {
    checkSuggestions("class A {{getParent<caret>OfType()} String getParentOfType() {return null;}}",
                     VariableKind.LOCAL_VARIABLE,
                     "getParentOfType", "parentOfType", "ofType", "type", "parent", "s", "string");
  }

  public void testNameByAssignmentContext() {
    checkSuggestions("class A {{String bar = \"<caret>\";}}",
                     VariableKind.PARAMETER,
                     "bar", "s", "string");
  }

  private void checkSuggestions(String code, VariableKind variableKind, String... expecteds) {
    Document fakeDocument = new DocumentImpl(code);
    EditorTestUtil.CaretAndSelectionState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);
    assertTrue("No caret specified", caretsState.hasExplicitCaret());
    PsiFile file = myFixture.configureByText("A.java", code);
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    SuggestedNameInfo nameInfo = JavaCodeStyleManager.getInstance(getProject()).suggestVariableName(variableKind, null, expression, null);
    Assert.assertArrayEquals("Suggested: " + Arrays.toString(nameInfo.names), expecteds, nameInfo.names);
  }
}
