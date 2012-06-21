package com.intellij.codeInsight.defaultAction;

import com.intellij.codeInsight.editorActions.CodeBlockEndAction;
import com.intellij.codeInsight.editorActions.CodeBlockStartAction;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.io.IOException;

import static com.intellij.testFramework.EditorTestUtil.BACKSPACE_FAKE_CHAR;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeEditorTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/defaultAction/customFileType/";

  private void _testBlockNavigation(String test, String ext) throws Exception {
    configureByFile(BASE_PATH + test + "." + ext);
    performEndBlockAction();
    checkResultByFile(BASE_PATH + test + "_after." + ext);

    configureByFile(BASE_PATH + test + "_after." + ext);
    performStartBlockAction();
    checkResultByFile(BASE_PATH + test + "." + ext);
  }

  private static void performStartBlockAction() {
    EditorActionHandler actionHandler = new CodeBlockStartAction().getHandler();

    actionHandler.execute(getEditor(), getCurrentEditorDataContext());
  }

  private static void performEndBlockAction() {
    EditorActionHandler actionHandler = new CodeBlockEndAction().getHandler();

    actionHandler.execute(getEditor(), getCurrentEditorDataContext());
  }

  public void testBlockNavigation() throws Exception {
    _testBlockNavigation("blockNavigation","cs");
  }

  public void testInsertDeleteQuotes() throws Exception {
    configureByFile(BASE_PATH + "InsertDeleteQuote.cs");
    EditorTestUtil.performTypingAction(getEditor(), '"');
    checkResultByFile(BASE_PATH + "InsertDeleteQuote_after.cs");

    configureByFile(BASE_PATH+"InsertDeleteQuote_after.cs");
    EditorTestUtil.performTypingAction(getEditor(), BACKSPACE_FAKE_CHAR);
    checkResultByFile(BASE_PATH+"InsertDeleteQuote.cs");

    FileType extension = FileTypeManager.getInstance().getFileTypeByExtension("pl");
    assertTrue("Test is not set up correctly:"+extension, extension instanceof AbstractFileType);
    configureByFile(BASE_PATH + "InsertDeleteQuote.pl");
    EditorTestUtil.performTypingAction(getEditor(), '"');
    checkResultByFile(BASE_PATH + "InsertDeleteQuote_after.pl");

    configureByFile(BASE_PATH+"InsertDeleteQuote_after.pl");
    EditorTestUtil.performTypingAction(getEditor(), BACKSPACE_FAKE_CHAR);
    checkResultByFile(BASE_PATH+"InsertDeleteQuote.pl");

    configureByFile(BASE_PATH + "InsertDeleteQuote.aj");
    EditorTestUtil.performTypingAction(getEditor(), '"');
    checkResultByFile(BASE_PATH + "InsertDeleteQuote_after.aj");

    configureByFile(BASE_PATH+"InsertDeleteQuote_after.aj");
    EditorTestUtil.performTypingAction(getEditor(), BACKSPACE_FAKE_CHAR);
    checkResultByFile(BASE_PATH+"InsertDeleteQuote.aj");
  }

  public void testInsertDeleteBracket() throws Exception {
    configureByFile(BASE_PATH + "InsertDeleteBracket.cs");
    EditorTestUtil.performTypingAction(getEditor(), '[');
    checkResultByFile(BASE_PATH + "InsertDeleteBracket_after.cs");

    configureByFile(BASE_PATH+"InsertDeleteBracket_after.cs");
    EditorTestUtil.performTypingAction(getEditor(), BACKSPACE_FAKE_CHAR);
    checkResultByFile(BASE_PATH+"InsertDeleteBracket.cs");

    configureByFile(BASE_PATH+"InsertDeleteBracket_after.cs");
    EditorTestUtil.performTypingAction(getEditor(), ']');
    checkResultByFile(BASE_PATH + "InsertDeleteBracket_after2.cs");
  }

  public void testInsertDeleteParenth() throws Exception {
    configureByFile(BASE_PATH + "InsertDeleteParenth.cs");
    EditorTestUtil.performTypingAction(getEditor(), '(');
    checkResultByFile(BASE_PATH + "InsertDeleteParenth_after.cs");

    configureByFile(BASE_PATH+"InsertDeleteParenth_after.cs");
    EditorTestUtil.performTypingAction(getEditor(), BACKSPACE_FAKE_CHAR);
    checkResultByFile(BASE_PATH+"InsertDeleteParenth.cs");

    configureByFile(BASE_PATH+"InsertDeleteParenth_after.cs");
    EditorTestUtil.performTypingAction(getEditor(), ')');
    checkResultByFile(BASE_PATH+"InsertDeleteParenth_after2.cs");

    configureByFile(BASE_PATH + "InsertDeleteParenth2_2.cs");
    EditorTestUtil.performTypingAction(getEditor(), '(');
    checkResultByFile(BASE_PATH + "InsertDeleteParenth2_2_after.cs");

    configureByFile(BASE_PATH + "InsertDeleteParenth2.cs");
    EditorTestUtil.performTypingAction(getEditor(), '(');
    checkResultByFile(BASE_PATH + "InsertDeleteParenth2_after.cs");
  }

  private void checkTyping(String fileName, String before, char typed, String after) throws IOException {
    configureFromFileText(fileName, before);
    EditorTestUtil.performTypingAction(getEditor(), typed);
    checkResultByText(after);
  }

  public void testParenthesesBeforeNonWs() throws Exception {
    checkTyping("a.cs", "<caret>a", '(', "(<caret>a");
    checkTyping("a.cs", "<caret>@a", '(', "(<caret>@a");
    checkTyping("a.cs", "<caret>(a", '(', "(<caret>(a");
    checkTyping("a.cs", "<caret>[a", '(', "(<caret>[a");
    checkTyping("a.cs", "<caret> (a", '(', "(<caret>) (a");
    checkTyping("a.cs", "(<caret>)", '(', "((<caret>))");
    checkTyping("a.cs", "(<caret>,)", '(', "((<caret>),)");

    checkTyping("a.cs", "<caret>a",   '[', "[<caret>a");
    checkTyping("a.cs", "<caret>@a",  '[', "[<caret>@a");
    checkTyping("a.cs", "<caret>(a",  '[', "[<caret>(a");
    checkTyping("a.cs", "<caret>[a",  '[', "[<caret>[a");
    checkTyping("a.cs", "<caret> (a", '[', "[<caret>] (a");
  }

  public void testQuoteBeforeNonWs() throws Exception {
    checkTyping("a.cs", "<caret>a", '"', "\"<caret>a");
    checkTyping("a.cs", "<caret> ", '"', "\"<caret>\" ");

    checkTyping("a.cs", "<caret>a", '\'', "'<caret>a");
    checkTyping("a.cs", "<caret> ", '\'', "'<caret>' ");
  }

  public void testReplaceQuote() throws Exception {
    checkTyping("a.cs", "<caret><selection>\"</selection>a\"", '\'', "\'<caret>a\"");
    checkTyping("a.cs", "<caret><selection>\"</selection>a\'", '\'', "\'<caret>a\'");
    checkTyping("a.cs", "\"a<caret><selection>\"</selection>", '\'', "\"a\'<caret>");
    checkTyping("a.cs", "\'a<caret><selection>\"</selection>", '\'', "\'a\'<caret>");

    checkTyping("a.cs", "<caret><selection>\'</selection>a\"", '\"', "\"<caret>a\"");
    checkTyping("a.cs", "<caret><selection>\'</selection>a\'", '\"', "\"<caret>a\'");
    checkTyping("a.cs", "\"a<caret><selection>\'</selection>", '\"', "\"a\"<caret>");
    checkTyping("a.cs", "\'a<caret><selection>\'</selection>", '\"', "\'a\"<caret>");
  }

  public void testNoPairedBracesInPlainText() throws Exception {
    checkTyping("a.txt", "<caret>", '(', "(<caret>");
    checkTyping("a.txt", "{<caret>}", '}', "{}<caret>}");
  }

  public void testClosingBraceInPlainText() throws Exception {
    configureFromFileText("a.txt", "<caret>");
    EditorTestUtil.performTypingAction(getEditor(), '(');
    EditorTestUtil.performTypingAction(getEditor(), ')');
    checkResultByText("()<caret>");
  }

  public void testInsertBraceOnEnter() throws Exception {
    configureByFile(BASE_PATH + "InsertBraceOnEnter.cs");
    EditorTestUtil.performTypingAction(getEditor(), '\n');
    checkResultByFile(BASE_PATH + "InsertBraceOnEnter_after.cs");
  }

  public void testInsertBraceOnEnterJavaFx() throws Exception {
    String testName = getTestName(false);
    configureByFile(BASE_PATH + testName + ".fx");
    EditorTestUtil.performTypingAction(getEditor(), '\n');
    checkResultByFile(BASE_PATH + testName + "_after.fx");
  }

  public void testCpp() throws Exception {
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(getProject(), "A.cpp");
    //                   0123456789012345678 9 0123 45 6 7
    highlighter.setText("#include try enum \"\\xff\\z\\\"xxx\"");
    HighlighterIterator iterator = highlighter.createIterator(2);
    assertEquals(CustomHighlighterTokenType.KEYWORD_1, iterator.getTokenType());

    iterator = highlighter.createIterator(9);
    assertEquals(CustomHighlighterTokenType.KEYWORD_2, iterator.getTokenType());

    iterator = highlighter.createIterator(15);
    assertEquals(CustomHighlighterTokenType.KEYWORD_1, iterator.getTokenType());

    iterator = highlighter.createIterator(19);
    assertEquals(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, iterator.getTokenType());

    iterator = highlighter.createIterator(23);
    assertEquals(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, iterator.getTokenType());

    iterator = highlighter.createIterator(25);
    assertEquals(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, iterator.getTokenType());

    iterator = highlighter.createIterator(27);
    assertEquals(CustomHighlighterTokenType.STRING, iterator.getTokenType());
  }

  public void testHaskel() throws Exception {
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(getProject(), "A.hs");
    //                   0123456789012345678 9 0123 45 6 7
    highlighter.setText("{-# #-} module");
    HighlighterIterator iterator = highlighter.createIterator(2);
    assertEquals(CustomHighlighterTokenType.MULTI_LINE_COMMENT, iterator.getTokenType());

    iterator = highlighter.createIterator(12);
    assertEquals(CustomHighlighterTokenType.KEYWORD_1, iterator.getTokenType());
  }
}
