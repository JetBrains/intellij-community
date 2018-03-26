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
package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.JavadocTypedHandler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 02/02/2011
 */
public class JavadocTypedHandlerTest {

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
