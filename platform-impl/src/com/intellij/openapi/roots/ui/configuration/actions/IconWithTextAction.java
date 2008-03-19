package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;

import javax.swing.*;


public abstract class IconWithTextAction extends AnAction implements CustomComponentAction {
  protected IconWithTextAction() {
  }

  protected IconWithTextAction(String text) {
    super(text);
  }

  protected IconWithTextAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public JComponent createCustomComponent(final Presentation presentation) {
    return createCustomComponentImpl(this, presentation);
  }

  public static JComponent createCustomComponentImpl(final AnAction action, final Presentation presentation) {
    return new ActionButtonWithText(action, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
