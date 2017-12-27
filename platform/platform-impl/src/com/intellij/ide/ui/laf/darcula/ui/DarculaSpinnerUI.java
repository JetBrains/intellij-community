/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerUI extends BasicSpinnerUI {
  private static final int ARROW_WIDTH = 7;
  private static final int ARROW_HEIGHT = 5;

  protected JButton prevButton;
  protected JButton nextButton;
  private FocusAdapter myFocusListener = new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
      spinner.repaint();
    }

    @Override
    public void focusLost(FocusEvent e) {
      spinner.repaint();
    }
  };

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaSpinnerUI();
  }

  private void addEditorFocusListener(JComponent editor) {
    if (editor != null) {
      editor.getComponents()[0].addFocusListener(myFocusListener);
    }
  }

  private void removeEditorFocusListener(JComponent editor) {
    if (editor != null) {
      editor.getComponents()[0].removeFocusListener(myFocusListener);
    }
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    removeEditorFocusListener(spinner.getEditor());
  }

  @Override
  protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
    super.replaceEditor(oldEditor, newEditor);
    removeEditorFocusListener(oldEditor);
    addEditorFocusListener(newEditor);
  }

  @Override
  protected JComponent createEditor() {
    JComponent editor = super.createEditor();
    addEditorFocusListener(editor);
    return editor;
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      float bw = bw();
      float arc = arc();

      g2.setColor(getBackground());
      Shape innerShape = new RoundRectangle2D.Float(bw, bw, c.getWidth() - bw * 2, c.getHeight() - bw * 2, arc, arc);
      g2.fill(innerShape);
    } finally {
      g2.dispose();
    }
  }

  protected Color getBackground() {
    return spinner.isEnabled() && spinner.getEditor() != null ? spinner.getEditor().getComponent(0).getBackground() : UIUtil.getPanelBackground();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    return getSizeWithButtons(c.getInsets(), size);
  }

  protected Dimension getSizeWithButtons(Insets i, Dimension size) {
    Dimension arrowSize = nextButton.getPreferredSize();
    Dimension minSize = new Dimension(i.left + JBUI.scale(20) + arrowSize.width, arrowSize.height * 2);
    size = isEmpty(size) ? minSize : new Dimension(Math.max(size.width, minSize.width), Math.max(size.height, minSize.height));

    Dimension editorSize = spinner.getEditor() != null ? spinner.getEditor().getPreferredSize() : JBUI.emptySize();
    return new Dimension(Math.max(size.width, i.left + editorSize.width + arrowSize.width),
                         Math.max(size.height, i.top + editorSize.height + i.bottom));
  }

  protected JButton createButton(@MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction, String name) {
    JButton button = createArrow(direction);
    button.setName(name);
    button.setBorder(JBUI.Borders.empty());
    if (direction == SwingConstants.NORTH) {
      installNextButtonListeners(button);
    } else {
      installPreviousButtonListeners(button);
    }
    return button;
  }

  @Override
  protected Component createPreviousButton() {
    return prevButton = createButton(SwingConstants.SOUTH, "Spinner.previousButton");
  }

  @Override
  protected Component createNextButton() {
    return nextButton = createButton(SwingConstants.NORTH, "Spinner.nextButton");
  }


  @Override
  protected LayoutManager createLayout() {
    return new LayoutManagerDelegate(super.createLayout()) {
      @Override
      public void layoutContainer(Container parent) {
        super.layoutContainer(parent);
        layout();
      }
    };
  }

  protected void layout() {
    int w = spinner.getWidth();
    int h = spinner.getHeight();

    Dimension abSize = nextButton.getPreferredSize();
    nextButton.setBounds(w - abSize.width, 0, abSize.width, h / 2);
    prevButton.setBounds(w - abSize.width, h/2, abSize.width, h - h/2);
  }

  protected void paintArrowButton(Graphics g,
                                  BasicArrowButton button,
                                  @MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction) {
    Insets i = spinner.getInsets();
    int x = (button.getWidth() - i.right - JBUI.scale(ARROW_WIDTH)) / 2;
    int y = direction == SwingConstants.NORTH ?
            button.getHeight() - JBUI.scale(2) :
            JBUI.scale(2);

    button.paintTriangle(g, x, y, 0, direction, spinner.isEnabled());
  }

  private JButton createArrow(@MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction) {
    BasicArrowButton b = new BasicArrowButton(direction) {
      @Override
      public void paint(Graphics g) {
        paintArrowButton(g, this, direction);
      }

      @Override
      public boolean isOpaque() {
        return false;
      }

      @Override
      public void paintTriangle(Graphics g, int x, int y, int size, int direction, boolean isEnabled) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                              MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

          float lw = lw(g2);
          float bw = bw();

          g2.setColor(getArrowButtonFillColor(spinner.hasFocus(), isEnabled, spinner.getBackground()));
          g2.fill(getInnerShape(lw, bw));

          // Paint side line
          Rectangle2D sideLine = direction == NORTH ?
                                 new Rectangle2D.Float(0, bw + lw, lw, getHeight() - (bw + lw)) :
                                 new Rectangle2D.Float(0, 0, lw, getHeight() - (bw + lw));

          g2.setColor(getArrowButtonFillColor(spinner.hasFocus(), isEnabled, getOutlineColor(isEnabled)));
          g2.fill(sideLine);

          // Paint arrow
          g2.translate(x, y);
          g2.setColor(new JBColor(Gray._255, isEnabled ? getForeground() : getOutlineColor(false)));
          g2.fill(getArrowShape());

        } finally {
          g2.dispose();
        }
      }

      private Shape getInnerShape(float lw, float bw) {
        Path2D shape = new Path2D.Float();
        int w = getWidth();
        int h = getHeight();
        float arc = arc() - bw - lw;

        switch (direction) {
          case SOUTH:
            shape.moveTo(lw, 0);
            shape.lineTo(w - bw - lw, 0);
            shape.lineTo(w - bw - lw, h - bw - lw - arc);
            shape.quadTo(w - bw - lw, h - bw - lw, w - bw - lw - arc, h - bw - lw);
            shape.lineTo(lw, h - bw - lw);
            shape.closePath();
            break;

          case NORTH:
            shape.moveTo(lw, bw + lw);
            shape.lineTo(w - bw - lw - arc, bw + lw);
            shape.quadTo(w - bw - lw, bw + lw , w - bw - lw, bw + lw + arc);
            shape.lineTo(w - bw - lw, h);
            shape.lineTo(lw, h);
            shape.closePath();
            break;
          default: break;
        }
        return shape;
      }

      private Shape getArrowShape() {
        Path2D arrow = new Path2D.Float();
        int aw = JBUI.scale(ARROW_WIDTH);
        int ah = JBUI.scale(ARROW_HEIGHT);

        switch (direction) {
          case SOUTH:
            arrow.moveTo(0, 0);
            arrow.lineTo(aw, 0);
            arrow.lineTo(aw / 2.0, ah);
            arrow.closePath();
            break;

          case NORTH:
            arrow.moveTo(0, 0);
            arrow.lineTo(aw, 0);
            arrow.lineTo(aw / 2.0, -ah);
            arrow.closePath();
            break;
          default: break;
        }

        return arrow;
      }

      @Override
      public Dimension getPreferredSize() {
        Insets i = spinner.getInsets();
        return new Dimension(JBUI.scale(12) + i.left,
                             JBUI.scale(9) + (direction == SwingConstants.NORTH ? i.top : i.bottom));
      }
    };

    b.setInheritsPopupMenu(true);
    b.setBorder(JBUI.Borders.empty());

    return b;
  }

  protected static class LayoutManagerDelegate implements LayoutManager {
    protected final LayoutManager myDelegate;

    public LayoutManagerDelegate(LayoutManager delegate) {
      myDelegate = delegate;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
      myDelegate.addLayoutComponent(name, comp);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      myDelegate.removeLayoutComponent(comp);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return myDelegate.preferredLayoutSize(parent);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return myDelegate.minimumLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
      myDelegate.layoutContainer(parent);
    }
  }
}
