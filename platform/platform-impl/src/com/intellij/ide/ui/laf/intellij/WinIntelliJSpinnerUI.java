/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerUI;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public class WinIntelliJSpinnerUI extends DarculaSpinnerUI {
  static final String HOVER_PROPERTY = "JSpinner.hover";

  static final int BUTTON_WIDTH = 20;
  static final int SPINNER_HEIGHT = 22;
  static final int EDITOR_OFFSET = 3;

  private MouseListener editorMouseListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJSpinnerUI();
  }

  @Override protected void installDefaults() {
    super.installDefaults();
    spinner.setOpaque(false);
  }

  @Override protected void installListeners() {
    super.installListeners();

    editorMouseListener = new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        spinner.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
        spinner.repaint();
      }

      @Override public void mouseExited(MouseEvent e) {
        spinner.putClientProperty(HOVER_PROPERTY, Boolean.FALSE);
        spinner.repaint();
      }
    };
    spinner.addMouseListener(editorMouseListener);
    getEditorFocusOwner(spinner).addMouseListener(editorMouseListener);
  }

  @Override protected void uninstallListeners() {
    super.uninstallListeners();
    if (editorMouseListener != null) {
      spinner.removeMouseListener(editorMouseListener);
      getEditorFocusOwner(spinner).removeMouseListener(editorMouseListener);
    }
  }

  private static Component getEditorFocusOwner(JSpinner spinner) {
    synchronized (spinner.getEditor().getTreeLock()) {
      return spinner.getEditor().getComponent(0);
    }
  }

  @Override public void paint(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Component parent = c.getParent();
      g2.setColor(c.isEnabled() ? UIManager.getColor("TextField.background") :
                  parent != null ? parent.getBackground() : UIManager.getColor("TextField.inactiveBackground"));
      g2.fillRect(0, 0, c.getWidth(), c.getHeight());
    } finally {
      g2.dispose();
    }
  }

  @Override protected JButton createButton(@MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction, String name) {
    JButton button = new BasicArrowButton(direction) {
      private final String iconName = "spinner" + (getDirection() == SwingConstants.NORTH ? "Up" : "Down") + "Triangle";

      @Override public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          int bw = JBUI.scale(1);
          ButtonModel bm = getModel();

          // set clip
          Area clip = new Area(g2.getClip());
          if ((DarculaSpinnerBorder.isFocused(spinner) || spinner.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE) &&
              !bm.isPressed()){
            clip.subtract(new Area(new Rectangle2D.Double(0, 0, bw, getHeight())));
          }

          if (getDirection() == SwingConstants.NORTH && prevButton.getModel().isRollover()) {
            clip.subtract(new Area(new Rectangle2D.Double(0, getHeight() - bw, getWidth(), bw)));
          } else if (getDirection() == SwingConstants.SOUTH && nextButton.getModel().isRollover()) {
            clip.subtract(new Area(new Rectangle2D.Double(0, 0, getWidth(), bw)));
          }
          g2.setClip(clip);

          // paint background
          Rectangle2D outerRect = new Rectangle2D.Double(0, 0, getWidth(), getHeight());
          if (spinner.isEnabled()) {
            if (bm.isPressed()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.pressedBackgroundColor"));
            } else if (bm.isRollover()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.focusedBackgroundColor"));
            } else {
              g2.setColor(UIManager.getColor("Button.background"));
            }
          } else {
            g2.setColor(UIManager.getColor("Button.background"));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
          }

          g2.fill(outerRect);

          // paint icon
          Icon icon = MacIntelliJIconCache.getIcon(iconName, false, false, isEnabled());
          icon.paintIcon(this, g2, JBUI.scale(5), JBUI.scale(3));

          // paint border
          if (spinner.isEnabled()) {
            if (bm.isPressed()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.pressedBorderColor"));
            } else if (bm.isRollover()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.focusedBorderColor"));
            } else {
              g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
            }
          } else {
            g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
          }

          Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
          border.append(outerRect, false);
          border.append(new Rectangle2D.Double(bw, bw, getWidth() - bw*2, getHeight() - bw*2), false);

          g2.fill(border);
        } finally {
          g2.dispose();
        }
      }

      @Override public Dimension getPreferredSize() {
        return new JBDimension(BUTTON_WIDTH, (getDirection() == SwingConstants.NORTH) ? 12 : 11);
      }

      @Override public Dimension getMaximumSize() {
        return getPreferredSize();
      }

      @Override public Dimension getMinimumSize() {
        return getPreferredSize();
      }
    };

    button.setName(name);
    button.setBorder(new EmptyBorder(1, 1, 1, 1));
    button.setRolloverEnabled(true);
    button.setOpaque(false);
    if (direction == SwingConstants.NORTH) {
      installNextButtonListeners(button);
    } else {
      installPreviousButtonListeners(button);
    }
    return button;
  }

  @Override
  protected LayoutManager createLayout() {
    return new LayoutManagerDelegate(super.createLayout()) {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension d = super.preferredLayoutSize(parent);
        if (d == null) return null;

        Insets i = parent.getInsets();
        int iw = i.left + i.right;
        return new JBDimension(Math.max(EDITOR_OFFSET + 20 + BUTTON_WIDTH - iw, d.width), SPINNER_HEIGHT);
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        Dimension d = super.minimumLayoutSize(parent);
        if (d == null) return null;

        Insets i = parent.getInsets();
        int iw = i.left + i.right;
        return new JBDimension(Math.max(EDITOR_OFFSET + 10 + BUTTON_WIDTH - iw, d.width), SPINNER_HEIGHT);
      }

      @Override
      public void layoutContainer(Container parent) {
        super.layoutContainer(parent);

        Rectangle bounds = parent.getBounds();

        Dimension nextButtonSize = nextButton.getPreferredSize();
        nextButton.setBounds(bounds.width - nextButtonSize.width, 0,
                             nextButtonSize.width, nextButtonSize.height);

        Dimension prevButtonSize = prevButton.getPreferredSize();
        prevButton.setBounds(bounds.width - prevButtonSize.width, nextButtonSize.height - JBUI.scale(1),
                             prevButtonSize.width, bounds.height - nextButtonSize.height + JBUI.scale(1));

        JComponent editor = spinner.getEditor();
        if (editor != null) {
          layoutEditor(editor);
        }
      }
    };
  }

  @Override
  protected void layoutEditor(@NotNull JComponent editor) {
    Rectangle bounds = editor.getBounds();
    Insets i = spinner.getInsets();

    int offset = JBUI.scale(EDITOR_OFFSET - i.left);
    editor.setBounds(bounds.x + offset,
                     bounds.y,
                     bounds.width - offset,
                     JBUI.scale(SPINNER_HEIGHT) - (i.top + i.bottom));
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getSizeWithButton(super.getPreferredSize(c));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getSizeWithButton(super.getMinimumSize(c));
  }

  private static Dimension getSizeWithButton(Dimension d) {
    if (d == null) return null;
    return new JBDimension(Math.max(d.width + 7, BUTTON_WIDTH), SPINNER_HEIGHT);
  }
}
