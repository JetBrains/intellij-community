// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class TailType {

  public static int insertChar(final Editor editor, final int tailOffset, final char c) {
    return insertChar(editor, tailOffset, c, true);
  }

  public static int insertChar(Editor editor, int tailOffset, char c, boolean overwrite) {
    Document document = editor.getDocument();
    int textLength = document.getTextLength();
    CharSequence chars = document.getCharsSequence();
    if (tailOffset == textLength || !overwrite || chars.charAt(tailOffset) != c){
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
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "UNKNOWN";
    }

  };

  public static final TailType NONE = new TailType(){
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "NONE";
    }
  };

  public static final TailType SEMICOLON = new CharTailType(';');
  @Deprecated public static final TailType EXCLAMATION = new CharTailType('!');

  public static final TailType COMMA = new TailType(){
    @Override
    public int processTail(final Editor editor, int tailOffset) {
      CommonCodeStyleSettings styleSettings = getLocalCodeStyleSettings(editor, tailOffset);
      if (styleSettings.SPACE_BEFORE_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ',');
      if (styleSettings.SPACE_AFTER_COMMA) tailOffset = insertChar(editor, tailOffset, ' ');
      return tailOffset;
    }

    public String toString() {
      return "COMMA";
    }
  };

  protected static CommonCodeStyleSettings getLocalCodeStyleSettings(Editor editor, int tailOffset) {
    final PsiFile psiFile = getFile(editor);
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    return CodeStyle.getLanguageSettings(psiFile, language);
  }

  protected static FileType getFileType(Editor editor) {
    PsiFile psiFile = getFile(editor);
    return psiFile.getFileType();
  }

  @NotNull
  private static PsiFile getFile(Editor editor) {
    Project project = editor.getProject();
    assert project != null;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert psiFile != null;
    return psiFile;
  }

  /**
   * insert a space, overtype if already present
   */
  public static final TailType SPACE = new CharTailType(' ');
  /**
   * always insert a space
   */
  public static final TailType INSERT_SPACE = new CharTailType(' ', false);
  /**
   * insert a space unless there's one at the caret position already, followed by a word
   */
  public static final TailType HUMBLE_SPACE_BEFORE_WORD = new CharTailType(' ', false) {

    @Override
    public boolean isApplicable(@NotNull InsertionContext context) {
      CharSequence text = context.getDocument().getCharsSequence();
      int tail = context.getTailOffset();
      if (text.length() > tail + 1 && text.charAt(tail) == ' ' && Character.isLetter(text.charAt(tail + 1))) {
        return false;
      }
      return super.isApplicable(context);
    }

    @Override
    public String toString() {
      return "HUMBLE_SPACE_BEFORE_WORD";
    }
  };
  public static final TailType DOT = new CharTailType('.');

  public static final TailType CASE_COLON = new CharTailType(':');
  public static final TailType COND_EXPR_COLON = new TailType(){
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      CharSequence chars = document.getCharsSequence();

      int afterWhitespace = CharArrayUtil.shiftForward(chars, tailOffset, " \n\t");
      if (afterWhitespace < textLength && chars.charAt(afterWhitespace) == ':') {
        return moveCaret(editor, tailOffset, afterWhitespace - tailOffset + 1);
      }
      document.insertString(tailOffset, " : ");
      return moveCaret(editor, tailOffset, 3);
    }

    public String toString() {
      return "COND_EXPR_COLON";
    }
  };

  public static final TailType EQ = new TailTypeEQ();

  public static class TailTypeEQ extends TailType {

    protected boolean isSpaceAroundAssignmentOperators(Editor editor, int tailOffset) {
      return getLocalCodeStyleSettings(editor, tailOffset).SPACE_AROUND_ASSIGNMENT_OPERATORS;
    }

    @Override
    public int processTail(final Editor editor, int tailOffset) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      CharSequence chars = document.getCharsSequence();
      if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == '='){
        return moveCaret(editor, tailOffset, 2);
      }
      if (tailOffset < textLength && chars.charAt(tailOffset) == '='){
        return moveCaret(editor, tailOffset, 1);
      }
      if (isSpaceAroundAssignmentOperators(editor, tailOffset)) {
        document.insertString(tailOffset, " =");
        tailOffset = moveCaret(editor, tailOffset, 2);
        tailOffset = insertChar(editor, tailOffset, ' ');
      }
      else{
        document.insertString(tailOffset, "=");
        tailOffset = moveCaret(editor, tailOffset, 1);
      }
      return tailOffset;
    }
  }

  public static final TailType LPARENTH = new CharTailType('(');

  public abstract int processTail(final Editor editor, int tailOffset);

  public static TailType createSimpleTailType(final char c) {
    return new CharTailType(c);
  }

  public boolean isApplicable(@NotNull final InsertionContext context) {
    return true;
  }
}
