// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//todo
public abstract class AbstractBasicJavaTypingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUpTypingTest(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(
      PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/codeInsight/typing");
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  protected @NotNull CodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter);
  }

  @Test
  public void testMulticaretSkipSemicolon() {
    doTest(';');
  }

  @Test
  public void testMulticaretSkipGt() {
    doTest('>');
  }

  @Test
  public void testMulticaretInsertGt() {
    doTest('<');
  }

  @Test
  public void testMulticaretSkipRParen() {
    doTest(')');
  }

  @Test
  public void testMulticaretInsertRParen() {
    doTest('(');
  }

  @Test
  public void testMulticaretSkipQuote() {
    doTest('"');
  }

  @Test
  public void testMulticaretInsertQuote() {
    doTest('"');
  }

  @Test
  public void testColumnMode() {
    CodeInsightTestFixture myFixture = getFixture();
    myFixture.configureByFile(getTestName(true) + "_before.java");
    EdtTestUtil.runInEdtAndWait(() -> ((EditorEx)myFixture.getEditor()).setColumnMode(true));
    myFixture.type('(');
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Test
  public void testInvalidInitialSyntax() {
    CodeInsightTestFixture myFixture = getFixture();
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type('\\');
    // emulates background commit after typing first character
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    myFixture.type('\\');
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Test
  public void testFixIfByBraceNewObject() {
    doTest('{');
  }

  @Test
  public void testFixIfByBraceCompositeCondition() {
    doTest('{');
  }

  @Test
  public void testInsertPairParenBeforeTryBlock() {
    doTest('(');
  }
  
  @Test
  public void testInsertPairedBraceBeforeDot() {
    doTest('{');
  }
  
  @Test
  public void testInsertPairedBraceForLambdaBody() {
    doTest('{');
  }
  
  @Test
  public void testInsertPairedBraceForLocalClass() {
    doTest('{');
  }
  
  @Test
  public void testInsertPairedBraceForLocalRecord() {
    doTest('{');
  }

  @Test
  public void testSemicolonInStringLiteral() {
    doTest(';');
  }

  @Test
  public void testSemicolonInComment() {
    doTest(';');
  }

  @Test
  public void testSemicolonBeforeRightParenMoved() {
    doMultiTypeTest(';');
  }

  @Test
  public void testSemicolonBeforeRightParenNotMoved() {
    doMultiTypeTest(';');
  }

  @Test
  public void testSemicolonBeforeRightParenInLiterals() {
    doMultiTypeTest(';');
  }

  @Test
  public void testSemicolonBeforeRightParenInBlockComment() {
    doTest(';');
  }

  @Test
  public void testCommaInDefaultAnnotationStringArgumentWhenArrayIsExpected() { doTest(','); }

  @Test
  public void testQuestionAfterPolyadicBoolean() { doTest('?'); }

  @Test
  public void testStartStringTemplate() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('{'); 
  }
  
  @Test
  public void testStartStringTemplateEof() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('{'); 
  }

  @Test
  public void testStartStringTemplateNoClosingQuote() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('{'); 
  }

  @Test
  public void testStartStringTemplateTextBlock() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('{'); 
  }

  @Test
  public void testStartStringTemplateAlreadyBrace() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('{'); 
  }
  
  @Test
  public void testEndStringTemplate() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('}');
  }
  
  @Test
  public void testEndStringTemplateTextBlock() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('}');
  }

  @Test
  public void testEndSecondStringTemplateTextBlock() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('}');
  }

  @Test
  public void testEndSecondStringTemplateNonEmptyExpression() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('}');
  }
  
  @Test
  public void testEndStringTemplateMiddleTextBlock() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('}');
  }
  
  @Test
  public void testUnrelatedBraceInStringTemplate() {
    setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
    doTest('}');
  }

  protected void setLanguageLevel(@NotNull LanguageLevel level) {
    IdeaTestUtil.setProjectLanguageLevel(getFixture().getProject(), level, getFixture().getTestRootDisposable());
  }

  protected void doTest(char c) {
    CodeInsightTestFixture myFixture = getFixture();
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type(c);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  protected void doTestUndo() {
    CodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      TextEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
      UndoManager.getInstance(myFixture.getProject()).undo(fileEditor);
    });
    myFixture.checkResultByFile(getTestName(true) + "_afterUndo.java");
  }

  private void doMultiTypeTest(char c) {
    CodeInsightTestFixture myFixture = getFixture();
    myFixture.configureByFile(getTestName(true) + "_before.java");
    List<Integer> whereToType = findWhereToType(myFixture.getEditor().getDocument().getImmutableCharSequence());
    assertNotNull(whereToType, "Test file must have at least one place where to type!");
    assertFalse(whereToType.isEmpty(), "Test file must have at least one place where to type!");
    EdtTestUtil.runInEdtAndWait(() -> {
      for (Integer offset : whereToType) {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type(c);
      }
    });
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
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
