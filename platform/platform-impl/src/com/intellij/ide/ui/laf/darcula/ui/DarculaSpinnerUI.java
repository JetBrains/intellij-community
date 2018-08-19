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

import com.intellij.util.ui.*;
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
  protected static final JBValue MINIMUM_WIDTH = new JBValue.Float(72);
  private static final JBValue ARROW_WIDTH = new JBValue.Float(9);
  private static final JBValue ARROW_HEIGHT = new JBValue.Float(5);

  protected Insets editorMargins() {
    return isCompact(spinner) ? JBUI.insets(0, 5) : JBUI.insets(1, 5);
  }

  protected JButton prevButton;
  protected JButton nextButton;
  private final FocusAdapter myFocusListener = new FocusAdapter() {
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

  private static void resetEditorOpaque(JComponent editor) {
    if (editor != null) {
      editor.setOpaque(false);
      ((JComponent)editor.getComponents()[0]).setOpaque(false);
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
    resetEditorOpaque(newEditor);
  }

  @Override
  protected JComponent createEditor() {
    JComponent editor = super.createEditor();
    addEditorFocusListener(editor);
    resetEditorOpaque(editor);
    return editor;
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(c.getSize());
    JBInsets.removeFrom(r, JBUI.insets(1));

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      float bw = BW.getFloat();
      float arc = COMPONENT_ARC.getFloat();

      g2.setColor(getBackground());
      g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
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
    Dimension minSize = new Dimension(i.left + MINIMUM_WIDTH.get() + i.right, arrowSize.height * 2);
    size = maximize(size, minSize);

    Dimension editorSize = spinner.getEditor() != null ? spinner.getEditor().getPreferredSize() : JBUI.emptySize();
    Insets m = editorMargins();
    return new Dimension(Math.max(size.width, i.left + m.left + editorSize.width + m.right + arrowSize.width),
                         Math.max(size.height, i.top + m.top + editorSize.height + m.bottom + i.bottom));
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
    nextButton.setBounds(w - abSize.width, JBUI.scale(1), abSize.width, h / 2);
    prevButton.setBounds(w - abSize.width, h/2, abSize.width, h - h/2);

    JComponent editor = spinner.getEditor();
    if (editor != null) {
      Insets i = spinner.getInsets();
      Insets m = editorMargins();
      int editorHeight = editor.getPreferredSize().height;
      int editorOffset = (int)Math.round((h - i.top - i.bottom - m.top - m.bottom - editorHeight) / 2.0);

      editor.setBounds(i.left + m.left,
                       i.top + m.top + editorOffset,
                       w - (i.left + abSize.width + m.left + m.right), editorHeight);
    }
  }

  protected void layoutEditor() {

  }

  protected void paintArrowButton(Graphics g,
                                  BasicArrowButton button,
                                  @MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction) {
    Insets i = spinner.getInsets();
    int x = (button.getWidth() - i.right - ARROW_WIDTH.get()) / 2;
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

          float lw = LW.getFloat();
          float bw = BW.getFloat();

          g2.setColor(getArrowButtonBackgroundColor(isEnabled, true));
          g2.fill(getInnerShape(lw, bw));

          // Paint side line
          int h = getHeight() - JBUI.scale(1);
          Rectangle2D sideLine = direction == NORTH ?
                                 new Rectangle2D.Float(0, bw + lw, lw, h - (bw + lw)) :
                                 new Rectangle2D.Float(0, 0, lw, h - (bw + lw));

          g2.setColor(getOutlineColor(spinner.isEnabled(), false));
          g2.fill(sideLine);

          // Paint arrow
          g2.translate(x, y);
          g2.setColor(getArrowButtonForegroundColor(isEnabled));
          g2.fill(getArrowShape());

        } finally {
          g2.dispose();
        }
      }

      private Shape getInnerShape(float lw, float bw) {
        Path2D shape = new Path2D.Float();
        int w = getWidth() - JBUI.scale(1);
        int h = getHeight() - JBUI.scale(1);
        float arc = COMPONENT_ARC.getFloat() - bw - lw;

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
        int aw = ARROW_WIDTH.get();
        int ah = ARROW_HEIGHT.get();

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
        int minHeight = isCompact(spinner) ? JBUI.scale(10) : JBUI.scale(12);
        return new Dimension(ARROW_BUTTON_WIDTH.get() + i.left,
                             minHeight + (direction == SwingConstants.NORTH ? i.top : i.bottom));
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
