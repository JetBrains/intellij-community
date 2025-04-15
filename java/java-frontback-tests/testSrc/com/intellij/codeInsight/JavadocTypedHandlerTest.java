// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.JavadocTypedHandler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public abstract class JavadocTypedHandlerTest {

  private static final String CARET_MARKER = "<caret>";
  
  @Test
  public void correctEmptyTagStart() {
    doTest("<first></first><second><caret>", "second");
  }
  
  @Test
  public void standaloneBracket() {
    doTest("asdf ><caret>", null);
  }

  @Test
  public void emptyElement() {
    doTest("<tag/><caret>", null);
  }

  @Test
  public void closingTag() {
    doTest("<tag></tag><caret>", null);
  }

  @Test
  public void startTagOnNewLine() {
    doTest("<t\nag><caret>", null);
  }
  
  @Test
  public void tagWithAttribute() {
    doTest("<a href='www'><caret>", "a");
  }
  
  private static void doTest(String text, String expected) {
    StringBuilder normalized = new StringBuilder();
    int offset = text.indexOf(CARET_MARKER);
    normalized.append(text, 0, offset);
    normalized.append(text.substring(offset + CARET_MARKER.length()));
    CharSequence actual = JavadocTypedHandler.getTagName(normalized.toString(), offset);
    assertEquals(expected, actual);
  }
}
