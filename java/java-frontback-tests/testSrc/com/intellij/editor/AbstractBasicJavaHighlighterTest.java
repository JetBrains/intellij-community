// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editor;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;

import java.util.ArrayList;

public abstract class AbstractBasicJavaHighlighterTest extends LightPlatformCodeInsightTestCase {
  protected EditorHighlighter myHighlighter;
  protected Document myDocument;
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

  protected Editor initDocument(String text) {
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
