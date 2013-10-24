/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * User: spLeaner
 */
public interface StatusBarWidget extends Disposable {
  enum PlatformType {
    DEFAULT, MAC
  }

  @NotNull
  String ID();

  @Nullable
  WidgetPresentation getPresentation(@NotNull PlatformType type);

  void install(@NotNull final StatusBar statusBar);

  interface Multiframe extends StatusBarWidget {
    StatusBarWidget copy();
  }

  interface WidgetPresentation {
    @Nullable
    String getTooltipText();

    @Nullable
    Consumer<MouseEvent> getClickConsumer();
  }

  interface IconPresentation extends WidgetPresentation {
    @NotNull
    Icon getIcon();
  }

  interface TextPresentation extends WidgetPresentation {
    @NotNull
    String getText();

    @NotNull
    @Deprecated
    String getMaxPossibleText();

    float getAlignment();
  }

  interface MultipleTextValuesPresentation extends WidgetPresentation {
    @Nullable("null means the widget is unable to show the popup")
    ListPopup getPopupStep();

    @Nullable
    String getSelectedValue();

    @NotNull
    @Deprecated
    String getMaxValue();
  }

  class WidgetBorder implements Border {
    public static final WidgetBorder INSTANCE = new WidgetBorder();

    private static final Color TOP = Gray._227;
    private static final Color LEFT1_FROM = Gray._161;
    private static final Color LEFT1_TO = Gray._133;
    private static final Color LEFT2_FROM = Gray._220;
    private static final Color LEFT2_TO = Gray._184;
    private static final Color LEFT1_FROM_INACTIVE = Gray._190;
    private static final Color PIXEL = LEFT1_FROM_INACTIVE;
    private static final Color LEFT1_TO_INACTIVE = Gray._180;

    private static final Color SEPARATOR_COLOR = UIUtil.getPanelBackground().darker();

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      if (UIUtil.isUnderDarcula()) return;
      final Graphics2D g2 = (Graphics2D)g.create();
      if (SystemInfo.isMac) {
        final Window window = SwingUtilities.getWindowAncestor(c);
        if (window != null && window.isActive()) {
          g2.setPaint(UIUtil.getGradientPaint(0, 0, LEFT1_FROM, 0, height, LEFT1_TO));
          g2.drawLine(x, y, x, y + height);

          g2.setPaint(UIUtil.getGradientPaint(0, 0, LEFT2_FROM, 0, height, LEFT2_TO));
          g2.drawLine(x + 1, y, x + 1, y + height);

          g2.setColor(PIXEL);
          g2.drawLine(x, y, x, y);

          g2.setColor(TOP);
          g2.drawLine(x + 2, y, x + width - 2, y);
        }
        else {
          g2.setPaint(UIUtil.getGradientPaint(0, 0, LEFT1_FROM_INACTIVE, 0, height, LEFT1_TO_INACTIVE));
          g2.drawLine(x, y, x, y + height);
        }
      } else {
        g2.setColor(UIUtil.isUnderIntelliJLaF() ? Gray._200 : SEPARATOR_COLOR);
        g2.drawLine(x, y, x, y + height);
      }

      g2.dispose();
    }

    public Insets getBorderInsets(Component c) {
      return new Insets(0, 4, 0, 2);
    }

    public boolean isBorderOpaque() {
      return false;
    }
  }
}
