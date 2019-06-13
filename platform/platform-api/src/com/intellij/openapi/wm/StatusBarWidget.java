// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.MouseEvent;

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
    @Nullable
    Icon getIcon();
  }

  interface TextPresentation extends WidgetPresentation {
    @NotNull
    String getText();

    /**
     * @deprecated unused
     */
    @NotNull
    @Deprecated
    default String getMaxPossibleText() { return ""; }

    float getAlignment();
  }

  interface MultipleTextValuesPresentation extends WidgetPresentation {
    @Nullable("null means the widget is unable to show the popup")
    ListPopup getPopupStep();

    @Nullable
    String getSelectedValue();

    /**
     * @deprecated unused
     */
    @NotNull
    @Deprecated
    default String getMaxValue() { return ""; }
  }

  abstract class WidgetBorder implements Border {
    public static final Border ICON = JBUI.Borders.empty(0, 4);
    public static final Border INSTANCE = JBUI.Borders.empty(0, 6);
    public static final Border WIDE = JBUI.Borders.empty(0, 6);
  }
}
