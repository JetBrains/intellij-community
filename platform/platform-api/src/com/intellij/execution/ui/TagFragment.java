// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class TagFragment<Settings> extends SettingsEditorFragment<Settings, JButton> {
  public TagFragment(String id, String name, String group, Predicate<Settings> getter, BiConsumer<Settings, Boolean> setter) {
    super(id, name, group, new TagButton(name),
          (settings, label) -> label.setVisible(getter.test(settings)),
          (settings, label) -> setter.accept(settings, label.isVisible()),
          getter);

    getComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_BACK_SPACE == e.getKeyCode() || KeyEvent.VK_DELETE == e.getKeyCode()) {
          setVisible(false);
        }
      }
    });
  }

  @Override
  public boolean isTag() {
    return true;
  }

  private static class TagButton extends JButton {
    private final static Color backgroundColor = Color.decode("#E5E5E5");

    private TagButton(String text) {
      super(text);
      setOpaque(false);
      putClientProperty("JButton.backgroundColor", backgroundColor);
    }

    @Override
    protected void paintComponent(Graphics g) {
      putClientProperty("JButton.borderColor", hasFocus() ? null : backgroundColor);
      super.paintComponent(g);
    }
  }
}
