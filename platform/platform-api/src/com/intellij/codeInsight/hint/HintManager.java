package com.intellij.codeInsight.hint;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author cdr
 */
public abstract class HintManager {
  public static HintManager getInstance() {
    return ServiceManager.getService(HintManager.class);
  }

  // Constants for 'constraint' parameter of showErrorHint()
  public static final short ABOVE = 1;
  public static final short UNDER = 2;
  public static final short LEFT = 3;
  public static final short RIGHT = 4;
  public static final short RIGHT_UNDER = 5;

  // Constants for 'flags' parameters
  public static final int HIDE_BY_ESCAPE = 0x01;
  public static final int HIDE_BY_ANY_KEY = 0x02;
  public static final int HIDE_BY_LOOKUP_ITEM_CHANGE = 0x04;
  public static final int HIDE_BY_TEXT_CHANGE = 0x08;
  public static final int HIDE_BY_OTHER_HINT = 0x10;
  public static final int HIDE_BY_SCROLLING = 0x20;
  public static final int HIDE_IF_OUT_OF_EDITOR = 0x40;
  public static final int UPDATE_BY_SCROLLING = 0x80;

  public abstract void showHint(@NotNull JComponent component, @NotNull RelativePoint p, int flags, int timeout);

  public abstract void showErrorHint(@NotNull Editor editor, @NotNull String text);

  public abstract void showInformationHint(@NotNull Editor editor, @NotNull String text);

  public abstract void showInformationHint(@NotNull Editor editor, @NotNull JComponent component);

  public abstract void showQuestionHint(@NotNull Editor editor, @NotNull String hintText, int offset1, int offset2, @NotNull QuestionAction action);

  public abstract boolean hideHints(int mask, boolean onlyOne, boolean editorChanged);

  public abstract void showErrorHint(@NotNull Editor editor, @NotNull String hintText, int offset1, int offset2, short constraint, int flags, int timeout);

  public abstract void hideAllHints();

  public abstract boolean hasShownHintsThatWillHideByOtherHint();
}
