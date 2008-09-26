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
  public abstract void showHint(@NotNull JComponent component, @NotNull RelativePoint p, int flags, int timeout);

  public abstract void showErrorHint(@NotNull Editor editor, String text);

  public abstract void showInformationHint(@NotNull Editor editor, String text);

  public abstract void showQuestionHint(
    Editor editor,
    String hintText,
    int offset1,
    int offset2,
    QuestionAction action);

  protected abstract boolean hideHints(int mask, boolean onlyOne, boolean editorChanged);

  public static HintManager getInstance() {
    return ServiceManager.getService(HintManager.class);
  }

  public abstract void showErrorHint(
    Editor editor,
    String hintText,
    int offset1,
    int offset2,
    short constraint,
    int flags,
    int timeout);

  public abstract void hideAllHints();

  public abstract boolean hasShownHintsThatWillHideByOtherHint();
}
