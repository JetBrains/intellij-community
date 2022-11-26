// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.editor;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;

import java.util.ArrayList;

public class JavaHighlighterTest extends LightJavaCodeInsightTestCase {
  private EditorHighlighter myHighlighter;
  private Document myDocument;
  private final ArrayList<Editor> myEditorsToRelease = new ArrayList<>();

  @Override
  protected void tearDown() throws Exception {
    try {
      for (Editor editor : myEditorsToRelease) {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testJavaDocCreation() {
    final String text1 = "{/*";
    String text = text1 + "\n";
    initDocument(text);

    WriteCommandAction.runWriteCommandAction(null, () -> myDocument.insertString(text1.length(), "*"));
  }

  public void testJavaDoc1() {
    final String text1 = "/**/";
    final String text = text1 + "public class B { }";
    initDocument(text);

    HighlighterIterator iterator = myHighlighter.createIterator(text1.length());
    assertEquals(JavaTokenType.PUBLIC_KEYWORD, iterator.getTokenType());
  }


  public void testJavaDocEditing1() {
    WriteCommandAction.runWriteCommandAction(null, () -> {
        final String text1 = "c/** * co *";
        String text = text1 + "class";
        initDocument(text);

        myDocument.insertString(text1.length(), "/");

        final HighlighterIterator iterator = myHighlighter.createIterator(text1.length() + 1);
        assertEquals(JavaTokenType.CLASS_KEYWORD, iterator.getTokenType());

        myDocument.deleteString(text1.length(), text1.length() + 1);
      });
  }

  public void testJavaDocEditing2() {
    WriteCommandAction.runWriteCommandAction(null, () -> {
        final String text1 = "/** ";
        final String text = text1 + "*/public class B { }";
        initDocument(text);

        HighlighterIterator iterator = myHighlighter.createIterator(text1.length() + 2);
        assertEquals(JavaTokenType.PUBLIC_KEYWORD, iterator.getTokenType());

        myDocument.deleteString(0, text1.length() + 1);

        iterator = myHighlighter.createIterator(1);
        assertEquals(JavaTokenType.PUBLIC_KEYWORD, iterator.getTokenType());
      });
  }

  public void testJavaDocHtmlEditing() {
    WriteCommandAction.runWriteCommandAction(null, () -> {
        final String text1 = "/**\n  * <co";
        final String text2 = text1 + " \n  * @param someParam\n  */";
        final String text3 = text2 + "class";
        initDocument(text3);

        HighlighterIterator iterator = myHighlighter.createIterator(text2.length());
        assertEquals(JavaTokenType.CLASS_KEYWORD, iterator.getTokenType());

        myDocument.insertString(text1.length(), "d");

        iterator = myHighlighter.createIterator(text2.length() + 1);
        assertEquals(JavaTokenType.CLASS_KEYWORD, iterator.getTokenType());

        myDocument.deleteString(text1.length(), text1.length() + 1);

        iterator = myHighlighter.createIterator(text2.length() + 1);
        assertEquals(JavaTokenType.CLASS_KEYWORD, iterator.getTokenType());
      });
  }

  public void testJavaDocTag1() {
    final String text1 = "/** ";
    String text = text1 + "@param */";
    initDocument(text);

    final HighlighterIterator iterator = myHighlighter.createIterator(text1.length());

    assertEquals(JavaDocTokenType.DOC_TAG_NAME, iterator.getTokenType());
  }

  public void testJavaDocTag3() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final String text1 = "/** @param <code";
      String text = text1 + " */";
      initDocument(text);

      final HighlighterIterator iterator = myHighlighter.createIterator(text1.length());

      myDocument.insertString(text1.length(), ">");
    });

//    assertEquals(JavaHighlightingLexer.DOC_TOKEN_SHIFT + JavaDocTokenType.DOC_TAG_NAME, iterator.getTokenType());
  }

  public void testJavaDocTag2() {
    final String text1 = "package some; /**\n  *  ";
    String text = text1 + "@param */";
    initDocument(text);

    final HighlighterIterator iterator = myHighlighter.createIterator(text1.length());

    assertEquals(JavaDocTokenType.DOC_TAG_NAME, iterator.getTokenType());
  }

  public void testEnteringSomeQuotes() {
    Editor editor = initDocument("""
                                   class C {
                                     void foo() {
                                       first();
                                       second();
                                     }
                                   }""");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      myDocument.insertString(myDocument.getText().lastIndexOf("first"), "'''");
      myDocument.insertString(myDocument.getText().lastIndexOf("second"), " ");
    });
    CheckHighlighterConsistency.performCheck(editor);
  }

  public void testUnicodeEscapeSequence() {
    String prefix = """
      class A {
        String s = ""\"
      """;
    initDocument(prefix +
                 "\\uuuuu005c\\\"\"\";\n" +
                 "}");
    HighlighterIterator iterator = myHighlighter.createIterator(prefix.length());
    assertEquals(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, iterator.getTokenType());
    iterator.advance();
    assertEquals(JavaTokenType.TEXT_BLOCK_LITERAL, iterator.getTokenType());
  }

  public void testUnicodeBackslashEscapesUnicodeSequence() {
    String prefix = """
      class A {
        String s = ""\"
      """;
    initDocument(prefix +
                 "\\u005c\\u0040\"\"\";\n" +
                 "}");
    HighlighterIterator iterator = myHighlighter.createIterator(prefix.length());
    assertEquals(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, iterator.getTokenType());
    iterator.advance();
    assertEquals(JavaTokenType.TEXT_BLOCK_LITERAL, iterator.getTokenType());
  }

  private Editor initDocument(String text) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    myDocument = editorFactory.createDocument(text);
    final Editor editor = editorFactory.createEditor(myDocument, getProject());

    myHighlighter = HighlighterFactory
      .createHighlighter(JavaFileType.INSTANCE, EditorColorsManager.getInstance().getGlobalScheme(), getProject());
    ((EditorEx)editor).setHighlighter(myHighlighter);

    myEditorsToRelease.add(editor);
    return editor;
  }
}
