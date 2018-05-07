// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.maximize;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJSpinnerUI extends DarculaSpinnerUI {
  private static final Icon DEFAULT_ICON = EmptyIcon.create(LafIconLookup.getIcon("spinnerRight"));

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJSpinnerUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Container parent = c.getParent();
    if (c.isOpaque() && parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    Insets i = c.getInsets();
    int x = c.getWidth() - DEFAULT_ICON.getIconWidth() - i.right;

    if (c instanceof JSpinner) {
      Graphics2D g2 = (Graphics2D)g;
      g2.setColor(getBackground());

      float arc = JBUI.scale(6f);
      Path2D rect = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      rect.moveTo(x, i.top);
      rect.lineTo(x, c.getHeight() - i.bottom);
      rect.lineTo(i.left + arc, c.getHeight() - i.bottom);
      rect.quadTo(i.left, c.getHeight() - i.bottom, i.left, c.getHeight() - i.bottom - arc);
      rect.lineTo(i.left, i.top + arc);
      rect.quadTo(i.left, i.top, i.left + arc, i.top);
      rect.closePath();

      g2.fill(rect);
    }

    Icon icon = LafIconLookup.getIcon("spinnerRight", false, false, c.isEnabled());
    icon.paintIcon(c, g, x, i.top);
  }

  @Override protected void paintArrowButton(Graphics g, BasicArrowButton button, int direction) {}

  protected Dimension getSizeWithButtons(Insets i, Dimension size) {
    int iconWidth = DEFAULT_ICON.getIconWidth() + i.right;
    int iconHeight = DEFAULT_ICON.getIconHeight() + i.top + i.bottom;

    Dimension minSize = new Dimension(i.left + MINIMUM_WIDTH.get() + i.right, iconHeight);
    size = maximize(size, minSize);

    Dimension editorSize = spinner.getEditor() != null ? spinner.getEditor().getPreferredSize() : JBUI.emptySize();
    Insets m = editorMargins();

    return new Dimension(Math.max(size.width, i.left + m.left + editorSize.width + m.right + iconWidth),
                         Math.max(size.height, i.top + m.top + editorSize.height + m.bottom + i.bottom));
  }

  @Override
  protected void layout() {
    JComponent editor = spinner.getEditor();
    if (editor != null) {
      Insets i = spinner.getInsets();
      Insets m = editorMargins();
      int editorHeight = editor.getPreferredSize().height;
      int editorOffset = (spinner.getHeight() - i.top - i.bottom - m.top - m.bottom - editorHeight) / 2;

      editor.setBounds(i.left + m.left,
                       i.top + m.top + editorOffset,
                       spinner.getWidth() - (i.left + i.right + DEFAULT_ICON.getIconWidth() + m.right + m.left),
                       editor.getPreferredSize().height);
    }
  }

  @Nullable Rectangle getArrowButtonBounds() {
    Insets i = spinner.getInsets();
    return new Rectangle(spinner.getWidth() - DEFAULT_ICON.getIconWidth() - i.right, i.top,
                         DEFAULT_ICON.getIconWidth(), DEFAULT_ICON.getIconHeight());
  }
}
