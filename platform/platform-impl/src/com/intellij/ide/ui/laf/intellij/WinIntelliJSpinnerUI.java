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

import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerUI;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import static com.intellij.util.ui.JBUI.*;

public class WinIntelliJSpinnerUI extends DarculaSpinnerUI {
  static final String HOVER_PROPERTY = "JSpinner.hover";

  static final int BUTTON_WIDTH = 21;
  static final int SPINNER_HEIGHT = 24;
  static final int EDITOR_OFFSET = 5;

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
        setHover(Boolean.TRUE);
      }

      @Override public void mouseExited(MouseEvent e) {
        setHover(Boolean.FALSE);
      }

      private void setHover(Boolean value) {
        if (spinner.isEnabled()) {
          spinner.putClientProperty(HOVER_PROPERTY, value);
          spinner.repaint();
        }
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
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Rectangle r = new Rectangle(c.getWidth(), c.getHeight());
      Container parent = c.getParent();
      if (c.isOpaque() && parent != null) {
        g2.setColor(parent.getBackground());
        g2.fill(r);
      }

      JBInsets.removeFrom(r, insets(2, 2, 2, BUTTON_WIDTH));
      g2.setColor(c.isEnabled() ? c.getBackground() : UIManager.getColor("Button.background"));

      if (!c.isEnabled()) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
      }

      g2.fill(r);

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
          int bw = scale(1);
          ButtonModel bm = getModel();

          // set clip
          Area clip = new Area(g2.getClip());
          if (!bm.isRollover() && !bm.isPressed()){
            clip.subtract(new Area(new Rectangle2D.Double(0, 0, bw, getHeight())));
          }

          if (getDirection() == SwingConstants.NORTH && prevButton.getModel().isRollover()) {
            clip.subtract(new Area(new Rectangle(0, getHeight() - bw, getWidth() - bw, bw)));
          } else if (getDirection() == SwingConstants.SOUTH && nextButton.getModel().isRollover()) {
            clip.subtract(new Area(new Rectangle(0, 0, getWidth() - bw, bw)));
          }
          g2.setClip(clip);

          // paint background
          Rectangle outerRect = new Rectangle(0, getDirection() == SwingConstants.NORTH ? bw : 0,
                                              getWidth() - bw, getHeight() - bw);
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
          icon.paintIcon(this, g2, scale(5), scale(3));

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

          Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
          border.append(outerRect, false);

          Rectangle innerRect = new Rectangle(outerRect);
          JBInsets.removeFrom(innerRect, JBUI.insets(1));
          border.append(innerRect, false);

          g2.fill(border);
        } finally {
          g2.dispose();
        }
      }

      @Override public Dimension getPreferredSize() {
        return new JBDimension(BUTTON_WIDTH, (getDirection() == SwingConstants.NORTH) ? 13 : 12);
      }
    };

    button.setName(name);
    button.setRolloverEnabled(true);
    button.setOpaque(false);
    if (direction == SwingConstants.NORTH) {
      installNextButtonListeners(button);
      button.setBorder(Borders.empty(2, 1, 1, 2));
    } else {
      installPreviousButtonListeners(button);
      button.setBorder(Borders.empty(1, 1, 2, 2));
    }
    return button;
  }

  @Override
  protected LayoutManager createLayout() {
    return new LayoutManagerDelegate(super.createLayout()) {
      @Override
      public void layoutContainer(Container parent) {
        super.layoutContainer(parent);

        Rectangle bounds = parent.getBounds();

        Dimension nextButtonSize = nextButton.getPreferredSize();
        Dimension prevButtonSize = prevButton.getPreferredSize();

        nextButtonSize.height = bounds.height * nextButtonSize.height / (nextButtonSize.height + prevButtonSize.height - scale(1));
        nextButton.setBounds(bounds.width - nextButtonSize.width, 0,
                             nextButtonSize.width, nextButtonSize.height);

        prevButton.setBounds(bounds.width - prevButtonSize.width, nextButtonSize.height - scale(1),
                             prevButtonSize.width, bounds.height - nextButtonSize.height + scale(1));

        JComponent editor = spinner.getEditor();
        if (editor != null) {
          layoutEditor(bounds, editor);
        }
      }
    };
  }

  @Override protected JComponent createEditor() {
    JComponent editor = super.createEditor();
    editor.setBorder(Borders.empty(1, 0));
    editor.setOpaque(false);
    return editor;
  }

  @Override protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
    super.replaceEditor(oldEditor, newEditor);
    newEditor.setBorder(Borders.empty(1, 0));
    newEditor.setOpaque(false);
  }

  private void layoutEditor(Rectangle pBounds, @NotNull JComponent editor) {
    Rectangle bounds = editor.getBounds();
    Insets i = spinner.getInsets();

    int offset = scale(EDITOR_OFFSET) - i.left;
    editor.setBounds(bounds.x + offset,
                     bounds.y,
                     bounds.width - offset,
                     pBounds.height - (i.top + i.bottom));
  }

  protected Dimension getSizeWithButtons(Insets i, Dimension size) {
     if (size == null) {
      Dimension editorSize = spinner.getEditor() != null ? spinner.getEditor().getPreferredSize() : emptySize();
      return new Dimension(i.left + Math.max(scale(EDITOR_OFFSET + 10), editorSize.width) + scale(BUTTON_WIDTH),
                           Math.max(scale(SPINNER_HEIGHT), i.top + editorSize.height + i.bottom));
    } else {
      return size;
    }
  }
}
