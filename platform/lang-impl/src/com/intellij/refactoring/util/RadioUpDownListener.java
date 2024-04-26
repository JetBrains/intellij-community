// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util;

import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class RadioUpDownListener extends KeyAdapter {
  private final JRadioButton[] myRadioButtons;

  private RadioUpDownListener(final JRadioButton... radioButtons) {
    myRadioButtons = radioButtons;
  }

  public static RadioUpDownListener installOn(final JRadioButton... radioButtons) {
    RadioUpDownListener listener = new RadioUpDownListener(radioButtons);
    listener.setupListeners();
    return listener;
  }

  private void setupListeners() {
    for (JRadioButton radioButton : myRadioButtons) {
      radioButton.addKeyListener(this);
    }
  }

  @Override
  public void keyPressed(final KeyEvent e) {
    final int selected = getSelected();
    if (selected != -1) {
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        up(selected, selected);
        e.consume();
      }
      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        down(selected, selected);
        e.consume();
      }
    }
  }

  private void down(int selected, int stop) {
    int newIdx = selected + 1;
    if (newIdx > myRadioButtons.length - 1) newIdx = 0;
    if (!click(myRadioButtons[newIdx]) && stop != newIdx) {
      down(newIdx, selected);
    }
  }

  private void up(int selected, int stop) {
    int newIdx = selected - 1;
    if (newIdx < 0) newIdx = myRadioButtons.length - 1;
    if (!click(myRadioButtons[newIdx]) && stop != newIdx) {
      up(newIdx, selected);
    }
  }

  private int getSelected() {
    for (int i = 0; i < myRadioButtons.length; i++) {
      if (myRadioButtons[i].isSelected()) {
        return i;
      }
    }
    return -1;
  }

  private static boolean click(final JRadioButton button) {
    if (button.isEnabled() && button.isVisible()) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(button, true));
      button.doClick();
      return true;
    }
    return false;
  }
}
