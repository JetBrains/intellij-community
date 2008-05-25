/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author peter
 */
public abstract class TailType {

  public static int insertChar(final Editor editor, final int tailOffset, final char c) {
    Document document = editor.getDocument();
    int textLength = document.getTextLength();
    CharSequence chars = document.getCharsSequence();
    if (tailOffset == textLength || chars.charAt(tailOffset) != c){
      document.insertString(tailOffset, String.valueOf(c));
    }
    return moveCaret(editor, tailOffset, 1);
  }

  protected static int moveCaret(final Editor editor, final int tailOffset, final int delta) {
    final CaretModel model = editor.getCaretModel();
    if (model.getOffset() == tailOffset) {
      model.moveToOffset(tailOffset + delta);
    }
    return tailOffset + delta;
  }

  public static final TailType UNKNOWN = new TailType(){
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "UNKNOWN";
    }

  };

  public static final TailType NONE = new TailType(){
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "NONE";
    }
  };

  public static final TailType SEMICOLON = new CharTailType(';');
  public static final TailType EXCLAMATION = new CharTailType('!');

  public static final TailType COMMA = new TailType(){
    public int processTail(final Editor editor, int tailOffset) {
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(editor.getProject());
      if (styleSettings.SPACE_BEFORE_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ',');
      if (styleSettings.SPACE_AFTER_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
      return tailOffset;
    }

    public String toString() {
      return "COMMA";
    }
  };
  public static final TailType SPACE = new CharTailType(' ');
  public static final TailType DOT = new CharTailType('.');
  public static final TailType QUOTE = new TailType() {
    public int processTail(final Editor editor, final int tailOffset) {
      final Document document = editor.getDocument();
      if (document.getTextLength() > tailOffset) {
        final char c = document.getCharsSequence().charAt(tailOffset);
        if (c == '\"' || c == '\'') {
          editor.getCaretModel().moveToOffset(tailOffset + 1);
          return tailOffset + 1;
        }
      }
      return tailOffset;
    }
  };

  public static final TailType CASE_COLON = new CharTailType(':');
  public static final TailType COND_EXPR_COLON = new TailType(){
    public int processTail(final Editor editor, final int tailOffset) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      CharSequence chars = document.getCharsSequence();

      if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == ':') {
        return moveCaret(editor, tailOffset, 2);
      }
      if (tailOffset < textLength && chars.charAt(tailOffset) == ':') {
        return moveCaret(editor, tailOffset, 1);
      }
      document.insertString(tailOffset, " : ");
      return moveCaret(editor, tailOffset, 3);
    }

    public String toString() {
      return "COND_EXPR_COLON";
    }
  };
  public static final TailType EQ = new TailType(){
    public int processTail(final Editor editor, int tailOffset) {
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(editor.getProject());
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      CharSequence chars = document.getCharsSequence();
      if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == '='){
        return moveCaret(editor, tailOffset, 2);
      }
      if (tailOffset < textLength && chars.charAt(tailOffset) == '='){
        return moveCaret(editor, tailOffset, 1);
      }
      if (styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS){
        document.insertString(tailOffset, " =");
        tailOffset = moveCaret(editor, tailOffset, 2);
      }
      else{
        document.insertString(tailOffset, "=");
        tailOffset = moveCaret(editor, tailOffset, 1);

      }
      if (styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS){
        tailOffset = insertChar(editor, tailOffset, ' ');
      }
      return tailOffset;
    }

    public String toString() {
      return "EQ";
    }
  };
  public static final TailType LPARENTH = new CharTailType('(');

  public abstract int processTail(final Editor editor, int tailOffset);

  public static TailType createSimpleTailType(final char c) {
    return new CharTailType(c);
  }
}
