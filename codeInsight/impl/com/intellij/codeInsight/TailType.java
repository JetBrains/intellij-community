/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.simple.CharTailType;
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
  };

  public static final TailType NONE = new TailType(){
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }
  };

  public static final TailType SEMICOLON = new CharTailType(';');

  public static final TailType COMMA = new TailType(){
    public int processTail(final Editor editor, int tailOffset) {
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(editor.getProject());
      if (styleSettings.SPACE_BEFORE_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ',');
      if (styleSettings.SPACE_AFTER_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
      return tailOffset;
    }
  };
  public static final TailType SPACE = new CharTailType(' ');
  public static final TailType DOT = new CharTailType('.');

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
  };
  public static final TailType LPARENTH = new TailType(){
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }
  };

  public abstract int processTail(final Editor editor, int tailOffset);

  public static TailType createSimpleTailType(final char c) {
    return new CharTailType(c);
  }
}
