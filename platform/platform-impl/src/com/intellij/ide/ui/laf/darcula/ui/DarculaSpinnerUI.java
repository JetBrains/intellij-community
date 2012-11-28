/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerUI extends BasicSpinnerUI {

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

  @Override
  protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
    super.replaceEditor(oldEditor, newEditor);
    if (oldEditor != null) {
      oldEditor.getComponents()[0].removeFocusListener(myFocusListener);
    }
    if (newEditor != null) {
      newEditor.getComponents()[0].addFocusListener(myFocusListener);
    }
  }

  @Override
  protected JComponent createEditor() {
    final JComponent editor = super.createEditor();
    editor.getComponents()[0].addFocusListener(myFocusListener);
    return editor;
  }

  @Override
  public void paint(Graphics g, JComponent c) {
  }

  @Override
  protected Component createPreviousButton() {
    JButton button = createArrow(SwingConstants.SOUTH);
    button.setName("Spinner.nextButton");
    button.setBorder(new EmptyBorder(1, 1, 1, 1));

    installNextButtonListeners(button);
    return button;
  }

  @Override
  protected Component createNextButton() {
    JButton button = createArrow(SwingConstants.NORTH);
    button.setName("Spinner.nextButton");
    button.setBorder(new EmptyBorder(1, 1, 1, 1));

    installNextButtonListeners(button);
    return button;
  }

  private static JButton createArrow(@MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction) {
    final Color shadow = UIUtil.getPanelBackground();
    final Color darkShadow = UIUtil.getLabelForeground();
    JButton b = new BasicArrowButton(direction, shadow, shadow, darkShadow, shadow) {
      @Override
      public void paintTriangle(Graphics g, int x, int y, int size, int direction, boolean isEnabled) {
        final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
        int mid;
        x = 5;
        y = 2;
        final int w = getWidth() - 1 - x - 2;
        final int h = getHeight() - 1 - 1;
        mid = (w + 1) / 2;
        g.setColor(UIUtil.getPanelBackground());
        g.fillRect(1, 1, getWidth() - 1, getHeight() - 1);

        g.setColor(isEnabled ? darkShadow : darkShadow.darker());

        g.translate(x, y);
        switch (direction) {
          case SOUTH:
            g.fillPolygon(new int[]{0, w, mid}, new int[]{1, 1, h}, 3);
            break;
          case NORTH:
            g.fillPolygon(new int[]{0, w, mid}, new int[]{h - 1, h - 1, 0}, 3);
            break;
          case WEST:
          case EAST:
        }
        g.translate(-x, -y);
        config.restore();
      }
    };
    Border buttonBorder = UIManager.getBorder("Spinner.arrowButtonBorder");
    if (buttonBorder instanceof UIResource) {
      // Wrap the border to avoid having the UIResource be replaced by
      // the ButtonUI. This is the opposite of using BorderUIResource.
      b.setBorder(new CompoundBorder(buttonBorder, null));
    }
    else {
      b.setBorder(buttonBorder);
    }
    b.setInheritsPopupMenu(true);
    return b;
  }
}
