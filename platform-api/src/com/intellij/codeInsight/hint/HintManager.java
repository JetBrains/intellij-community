package com.intellij.codeInsight.hint;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author cdr
 */
public abstract class HintManager implements ApplicationComponent {
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
    return ApplicationManager.getApplication().getComponent(HintManager.class);
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
