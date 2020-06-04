// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.openapi.util.NlsContexts.HintText;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

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
  public static final short DEFAULT = 6;

  @MagicConstant(intValues = {ABOVE, UNDER, LEFT, RIGHT, RIGHT_UNDER, DEFAULT})
  public @interface PositionFlags {}


  // Constants for 'flags' parameters
  public static final int HIDE_BY_ESCAPE = 0x01;
  public static final int HIDE_BY_ANY_KEY = 0x02;
  public static final int HIDE_BY_LOOKUP_ITEM_CHANGE = 0x04;
  public static final int HIDE_BY_TEXT_CHANGE = 0x08;
  public static final int HIDE_BY_OTHER_HINT = 0x10;
  public static final int HIDE_BY_SCROLLING = 0x20;
  public static final int HIDE_IF_OUT_OF_EDITOR = 0x40;
  public static final int UPDATE_BY_SCROLLING = 0x80;
  public static final int HIDE_BY_MOUSEOVER = 0x100;
  public static final int DONT_CONSUME_ESCAPE = 0x200;
  public static final int HIDE_BY_CARET_MOVE = 0x400;

  @MagicConstant(flags = {HIDE_BY_ESCAPE, HIDE_BY_ANY_KEY, HIDE_BY_LOOKUP_ITEM_CHANGE, HIDE_BY_TEXT_CHANGE, HIDE_BY_OTHER_HINT, HIDE_BY_SCROLLING, HIDE_IF_OUT_OF_EDITOR, UPDATE_BY_SCROLLING, HIDE_BY_MOUSEOVER, DONT_CONSUME_ESCAPE, HIDE_BY_CARET_MOVE})
  public @interface HideFlags {}

  public abstract void showHint(@NotNull JComponent component, @NotNull RelativePoint p, @HideFlags int flags, int timeout);

  public abstract void showErrorHint(@NotNull Editor editor, @NotNull @HintText String text);
  public abstract void showErrorHint(@NotNull Editor editor, @NotNull @HintText String text, @PositionFlags short position);

  public void showInformationHint(@NotNull Editor editor, @NotNull @HintText String text) {
    showInformationHint(editor, text, ABOVE);
  }
  public abstract void showInformationHint(@NotNull Editor editor, @NotNull @HintText String text, @PositionFlags short position);
  public abstract void showInformationHint(@NotNull Editor editor, @NotNull @HintText String text, @Nullable HyperlinkListener listener);

  public abstract void showInformationHint(@NotNull Editor editor, @NotNull JComponent component);

  public abstract void showQuestionHint(@NotNull Editor editor, @NotNull @HintText String hintText, int offset1, int offset2, @NotNull QuestionAction action);

  public abstract boolean hideHints(@HideFlags int mask, boolean onlyOne, boolean editorChanged);

  public abstract void showErrorHint(@NotNull Editor editor, @NotNull @HintText String hintText, int offset1, int offset2, @PositionFlags short constraint, @HideFlags int flags, int timeout);

  public abstract void hideAllHints();

  public abstract boolean hasShownHintsThatWillHideByOtherHint(boolean willShowTooltip);

  public abstract void setRequestFocusForNextHint(boolean requestFocus);
}
