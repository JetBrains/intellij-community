// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.MouseEvent;

/**
 * @see StatusBarWidgetFactory
 */
public interface StatusBarWidget extends Disposable {
  /**
   * @deprecated do not use it
   */
  @Deprecated(forRemoval = true)
  enum PlatformType {
    DEFAULT, MAC
  }

  @NonNls @NotNull
  String ID();

  @Nullable
  default WidgetPresentation getPresentation() {
    return getPresentation(SystemInfo.isMac ? PlatformType.MAC : PlatformType.DEFAULT);
  }

  /**
   * @deprecated use this{@link #getPresentation()} instead
   */
  @Deprecated(forRemoval = true)
  @Nullable
  default WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  void install(@NotNull StatusBar statusBar);

  interface Multiframe extends StatusBarWidget {
    StatusBarWidget copy();
  }

  interface WidgetPresentation {
    @Nullable
    @Tooltip
    String getTooltipText();

    @Nls
    @Nullable
    default String getShortcutText() { return null; }

    @Nullable
    Consumer<MouseEvent> getClickConsumer();
  }

  interface IconPresentation extends WidgetPresentation {
    @Nullable
    Icon getIcon();
  }

  interface TextPresentation extends WidgetPresentation {
    @NotNull
    @NlsContexts.Label
    String getText();

    /**
     * @deprecated unused
     */
    @NotNull
    @Deprecated(forRemoval = true)
    default String getMaxPossibleText() { return ""; }

    float getAlignment();
  }

  interface MultipleTextValuesPresentation extends WidgetPresentation {
    @Nullable("null means the widget is unable to show the popup")
    ListPopup getPopupStep();

    @Nullable @NlsContexts.StatusBarText
    String getSelectedValue();

    /**
     * @deprecated unused
     */
    @NotNull
    @Deprecated(forRemoval = true)
    default String getMaxValue() { return ""; }

    @Nullable
    default Icon getIcon() {
      return null;
    }
  }

  /**
   * @deprecated Use {@link JBUI.CurrentTheme.StatusBar.Widget} border methods
   */
  @Deprecated
  abstract class WidgetBorder implements Border {
    public static final Border ICON = JBUI.CurrentTheme.StatusBar.Widget.iconBorder();
    public static final Border INSTANCE = JBUI.CurrentTheme.StatusBar.Widget.border();
    public static final Border WIDE = JBUI.CurrentTheme.StatusBar.Widget.border();
  }
}
