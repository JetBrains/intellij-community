// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaTypingTest extends BasePlatformTestCase {
  public void testIndentRBrace() {
    doTest('}');
    doTestUndo();
  }

  public void testMulticaretIndentLBrace() {
    doTest('{');
  }

  public void testMulticaretIndentRBrace() {
    doTest('}');
  }

  public void testMulticaretSkipSemicolon() {
    doTest(';');
  }

  public void testMulticaretSkipGt() {
    doTest('>');
  }

  public void testMulticaretInsertGt() {
    doTest('<');
  }

  public void testMulticaretSkipRParen() {
    doTest(')');
  }

  public void testMulticaretInsertRParen() {
    doTest('(');
  }

  public void testMulticaretSkipQuote() {
    doTest('"');
  }

  public void testMulticaretInsertQuote() {
    doTest('"');
  }

  public void testColumnMode() {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    ((EditorEx)myFixture.getEditor()).setColumnMode(true);
    myFixture.type('(');
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testInvalidInitialSyntax() {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type('\\');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); // emulates background commit after typing first character
    myFixture.type('\\');
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testFixIfByBrace() {
    doTest('{');
  }

  public void testFixIfByBraceNewObject() {
    doTest('{');
  }

  public void testFixIfByBraceCompositeCondition() {
    doTest('{');
  }

  public void testFixWhileByBrace() {
    doTest('{');
  }
  
  public void testInsertPairParenBeforeTryBlock() {
    doTest('(');
  }
  
  public void testInsertPairedBraceBeforeDot() {
    doTest('{');
  }
  
  public void testInsertPairedBraceForLambdaBody() {
    doTest('{');
  }
  
  public void testInsertPairedBraceForLocalClass() {
    doTest('{');
  }
  
  public void testInsertPairedBraceForLocalRecord() {
    doTest('{');
  }

  public void testSemicolonInStringLiteral() {
    doTest(';');
  }

  public void testSemicolonInComment() {
    doTest(';');
  }

  public void testSemicolonBeforeRightParenMoved() {
    doMultiTypeTest(';');
  }

  public void testSemicolonBeforeRightParenNotMoved() {
    doMultiTypeTest(';');
  }

  public void testSemicolonBeforeRightParenInLiterals() {
    doMultiTypeTest(';');
  }

  public void testSemicolonBeforeRightParenInBlockComment() {
    doTest(';');
  }

  public void testCommaAfterDefaultAnnotationArgumentWhenArrayIsExpected() {
    doTest(',');
  }

  public void testCommaInDefaultAnnotationStringArgumentWhenArrayIsExpected() { doTest(','); }

  public void testQuestionAfterPolyadic() { doTest('?'); }
  public void testQuestionAfterPolyadic2() { doTest('?'); }

  public void testQuestionAfterPolyadicBoolean() { doTest('?'); }

  public void testEqualAfterBitwiseOp() { doTest('='); }

  public void testDotOnNewLine() { doTest('.'); }

  public void testEqualAfterBitwiseOp2() {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(myFixture.getFile());
    settings.SPACE_WITHIN_PARENTHESES = true;
    try {
      myFixture.type('=');
      myFixture.checkResultByFile(getTestName(true) + "_after.java");
    }
    finally {
      settings.SPACE_WITHIN_PARENTHESES = false;
    }
  }

  private void doTest(char c) {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type(c);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  private void doTestUndo() {
    TextEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
    UndoManager.getInstance(getProject()).undo(fileEditor);
    myFixture.checkResultByFile(getTestName(true) + "_afterUndo.java");
  }

  private void doMultiTypeTest(char c) {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    List<Integer> whereToType = findWhereToType(myFixture.getEditor().getDocument().getImmutableCharSequence());
    assertNotNull("Test file must have at least one place where to type!", whereToType);
    assertFalse("Test file must have at least one place where to type!", whereToType.isEmpty());
    for (Integer offset : whereToType) {
      myFixture.getEditor().getCaretModel().moveToOffset(offset);
      myFixture.type(c);
    }
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/codeInsight/typing";
  }

  private static List<Integer> findWhereToType(@NotNull CharSequence content) {
    List<Integer> offsets = new ArrayList<>();
    Matcher m = Pattern.compile("/\\*typehere\\*/").matcher(content);
    while (m.find()) {
      offsets.add(m.end());
    }
    offsets.sort(Comparator.reverseOrder());
    return offsets;
  }
}
