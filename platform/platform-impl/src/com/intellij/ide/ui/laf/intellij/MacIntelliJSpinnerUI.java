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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerUI;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJSpinnerUI extends DarculaSpinnerUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJSpinnerUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    JComponent editor = spinner.getEditor();
    boolean hasFocus = c.hasFocus();
    if (!hasFocus && editor != null) {
      hasFocus = editor.hasFocus();
      if (!hasFocus) {
        for (JComponent child : UIUtil.findComponentsOfType(editor, JComponent.class)) {
          hasFocus = child.hasFocus();
          if (hasFocus) break;
        }
      }
    }

    Container parent = c.getParent();
    if (c.isOpaque() && parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0,0,c.getWidth(),c.getHeight());
    }
    Insets clip = c.getInsets();
    int stop = c.getWidth() - MacIntelliJIconCache.getIcon("spinnerRight").getIconWidth() - clip.right;
    //int y = (c.getHeight() - 26) / 2;
    Graphics gg = g.create(clip.left, clip.top, stop - clip.left, MacIntelliJIconCache.getIcon("spinnerRight").getIconHeight());
    boolean enabled = c.isEnabled();
    Icon icon = MacIntelliJIconCache.getIcon("comboLeft", false, hasFocus, enabled);
    icon.paintIcon(c,gg,clip.left,clip.top);
    int x = icon.getIconWidth();
    icon = MacIntelliJIconCache.getIcon("comboMiddle", false, hasFocus, enabled);
    while (x < stop) {
      icon.paintIcon(c, gg, x, clip.top);
      x+=icon.getIconWidth();
    }
    gg.dispose();
    icon = MacIntelliJIconCache.getIcon("spinnerRight", false, hasFocus, enabled);
    icon.paintIcon(c, g, stop, clip.top);
  }

  @Override
  protected void paintArrowButton(Graphics g,
                                  BasicArrowButton button,
                                  @MagicConstant(intValues = {SwingConstants.NORTH, SwingConstants.SOUTH}) int direction) {
  }

  @Override
  protected LayoutManager createLayout() {
    return new LayoutManagerDelegate(super.createLayout()) {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        return toConstantHeight(super.preferredLayoutSize(parent), 26);
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        return toConstantHeight(super.minimumLayoutSize(parent), 26);
      }
    };
  }

  @Override
  protected void layoutEditor(@NotNull JComponent editor) {
    int w = spinner.getWidth();
    int h = spinner.getHeight();
    JBInsets insets = JBUI.insets(spinner.getInsets());
    editor.setBounds(insets.left + 5, insets.top + 5, w - 5 - 26 - insets.width(), h - insets.height() - 10);
  }

  private static Dimension toConstantHeight(@Nullable Dimension size, int height) {
    return size == null ? null : new Dimension(size.width, height);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return toConstantHeight(super.getPreferredSize(c), 26);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return toConstantHeight(super.getMinimumSize(c), 26);
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return toConstantHeight(super.getMaximumSize(c), 26);
  }
}
