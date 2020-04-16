// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class TagFragment<Settings> extends SettingsEditorFragment<Settings, JLabel> {
  public TagFragment(String name, Predicate<Settings> getter, BiConsumer<Settings, Boolean> setter) {
    super(name, new TagLabel(name),
          (settings, label) -> label.setVisible(getter.test(settings)),
          (settings, label) -> setter.accept(settings, label.isVisible()));
  }

  @Override
  public boolean isTag() {
    return true;
  }

  private static class TagLabel extends JLabel {
    private TagLabel(String text) {
      super(text);
      setOpaque(false);
      setBackground(Color.decode("#E5E5E5"));
      setBorder(JBUI.Borders.empty(4, 6));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D) g.create();
      Insets insets = getBorder().getBorderInsets(this);
      g2d.setColor(getBackground());
      RectanglePainter2D.FILL.paint(g2d, 0, 0, getWidth(), getHeight(), (double)insets.left);
      super.paintComponent(g);
    }
  }
}
