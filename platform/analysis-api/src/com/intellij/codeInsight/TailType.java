// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * An object representing a simple document change done at {@link InsertionContext#getTailOffset()} after completion,
 * namely, inserting a character, sometimes with spaces for formatting.
 * Please consider putting this logic into {@link com.intellij.codeInsight.lookup.LookupElement#handleInsert} or
 * {@link com.intellij.codeInsight.completion.InsertHandler},
 * as they're more flexible, and having all document modification code in one place will probably be more comprehensive.
 * <p/>
 * <h3><a id="deprecated-constants">Deprecated constants</a></h3>
 * All static fields in this class representing instances of {@link CharTailType} are deprecated. It was done to avoid deadlocks
 * during initialization of the class. According to <a href="https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5">section 5.5</a>
 * of JVM specification, when a class is initialized, JVM firstly synchronizes on an initialization lock specific for that class, then
 * initializes its super class and then computes initializers for its static fields. So because of these fields initialization of {@link TailType}
 * performs initialization of {@link CharTailType}, and initialization of {@link CharTailType} performs initialization of {@link TailType}
 * because it's the super class. Therefore, if one thread starts initialization of {@link TailType}, and another thread
 * starts initialization of {@link CharTailType} at the same time, it'll result in a deadlock. In order to avoid this, static fields from
 * {@link TailTypes} must be used.
 */
public abstract class TailType {
  public abstract int processTail(final Editor editor, int tailOffset);

  public boolean isApplicable(final @NotNull InsertionContext context) {
    return true;
  }

  public static int insertChar(final Editor editor, final int tailOffset, final char c) {
    return insertChar(editor, tailOffset, c, true);
  }

  public static int insertChar(Editor editor, int tailOffset, char c, boolean overwrite) {
    Document document = editor.getDocument();
    int textLength = document.getTextLength();
    CharSequence chars = document.getCharsSequence();
    if (tailOffset == textLength || !overwrite || chars.charAt(tailOffset) != c) {
      document.insertString(tailOffset, String.valueOf(c));
    }
    return moveCaret(editor, tailOffset, 1);
  }

  public static TailType createSimpleTailType(final char c) {
    return new CharTailType(c);
  }

  protected static int moveCaret(final Editor editor, final int tailOffset, final int delta) {
    final CaretModel model = editor.getCaretModel();
    if (model.getOffset() == tailOffset) {
      model.moveToOffset(tailOffset + delta);
    }
    return tailOffset + delta;
  }

  protected static FileType getFileType(Editor editor) {
    PsiFile psiFile = getFile(editor);
    return psiFile.getFileType();
  }

  private static @NotNull PsiFile getFile(Editor editor) {
    Project project = editor.getProject();
    assert project != null;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert psiFile != null;
    return psiFile;
  }

  //<editor-fold desc="Deprecated static constants">
  /** @deprecated use {@link TailTypes#unknownType()} instead. */
  @Deprecated(forRemoval = true)
  public static final TailType UNKNOWN = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "UNKNOWN";
    }
  };

  /** @deprecated use {@link TailTypes#noneType()} instead. */
  @Deprecated(forRemoval = true)
  public static final TailType NONE = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "NONE";
    }
  };

  /** @deprecated use {@link TailTypes#semicolonType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated(forRemoval = true)
  public static final TailType SEMICOLON = new CharTailType(';');

  /**
   * insert a space, overtype if already present
   *
   * @deprecated use {@link TailTypes#spaceType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details.
   */
  @Deprecated(forRemoval = true)
  public static final TailType SPACE = new CharTailType(' ');

  /**
   * always insert a space
   *
   * @deprecated use {@link TailTypes#insertSpaceType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details.
   */
  @Deprecated(forRemoval = true)
  public static final TailType INSERT_SPACE = new CharTailType(' ', false);

  /**
   * insert a space unless there's one at the caret position already, followed by a word or '@'
   *
   * @deprecated use {@link TailTypes#humbleSpaceBeforeWordType()} instead,
   * see <a href="#deprecated-constants">Deprecated Constants</a> for details.
   */
  @Deprecated(forRemoval = true)
  public static final TailType HUMBLE_SPACE_BEFORE_WORD = new CharTailType(' ', false) {
    @Override
    public boolean isApplicable(@NotNull InsertionContext context) {
      CharSequence text = context.getDocument().getCharsSequence();
      int tail = context.getTailOffset();
      if (text.length() > tail + 1 && text.charAt(tail) == ' ') {
        char ch = text.charAt(tail + 1);
        if (ch == '@' || Character.isLetter(ch)) {
          return false;
        }
      }
      return super.isApplicable(context);
    }

    @Override
    public String toString() {
      return "HUMBLE_SPACE_BEFORE_WORD";
    }
  };

  /** @deprecated use {@link TailTypes#dotType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated(forRemoval = true)
  public static final TailType DOT = new CharTailType('.');

  /** @deprecated use {@link TailTypes#caseColonType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated(forRemoval = true)
  public static final TailType CASE_COLON = new CharTailType(':');

  /** @deprecated use {@link TailTypes#equalsType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated(forRemoval = true)
  public static final TailType EQUALS = new CharTailType('=');

  /** @deprecated use {@link TailTypes#conditionalExpressionColonType()} instead. */
  @Deprecated(forRemoval = true)
  public static final TailType COND_EXPR_COLON = new TailType() {
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

  /**
   * @deprecated create <code>new CharTailType('(')</code> instance instead,
   * see <a href="#deprecated-constants">Deprecated Constants</a> for details.
   */
  @Deprecated(forRemoval = true)
  public static final TailType LPARENTH = new CharTailType('(');
  //</editor-fold>
}
