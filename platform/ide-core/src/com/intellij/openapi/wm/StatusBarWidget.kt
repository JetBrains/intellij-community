// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.MouseEvent;

/**
 * @see StatusBarWidgetFactory
 */
public interface StatusBarWidget extends Disposable {
  @NotNull @NonNls String ID();

  default @Nullable WidgetPresentation getPresentation() {
    return getPresentation(SystemInfoRt.isMac ? PlatformType.MAC : PlatformType.DEFAULT);
  }

  default void install(@NotNull StatusBar statusBar) {
  }

  @Override
  default void dispose() {
  }

  @SuppressWarnings("SpellCheckingInspection")
  interface Multiframe extends StatusBarWidget {
    StatusBarWidget copy();
  }

  interface WidgetPresentation {
    @Nullable @NlsContexts.Tooltip String getTooltipText();

    default @Nullable @Nls String getShortcutText() {
      return null;
    }

    default @Nullable Consumer<MouseEvent> getClickConsumer() {
      return null;
    }
  }

  interface IconPresentation extends WidgetPresentation {
    /**
     * @deprecated Use {@link #getIcon(StatusBar)}
     */
    @Deprecated
    default @Nullable Icon getIcon() {
      throw new AbstractMethodError();
    }

    default @Nullable Icon getIcon(@SuppressWarnings("unused") @NotNull StatusBar statusBar) {
      return getIcon();
    }
  }

  interface TextPresentation extends WidgetPresentation {
    @NotNull @NlsContexts.Label String getText();

    /** @deprecated never invoked; please drop */
    @Deprecated(forRemoval = true)
    default @NotNull String getMaxPossibleText() { return ""; }

    float getAlignment();
  }

  interface MultipleTextValuesPresentation extends WidgetPresentation {
    /**
     * @deprecated implement {@link #getPopup()}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = true)
    default @Nullable("null means the widget is unable to show the popup") ListPopup getPopupStep() {
      return null;
    }

    @Nullable("null means the widget is unable to show the popup")
    default JBPopup getPopup() {
      return getPopupStep();
    }

    @Nullable @NlsContexts.StatusBarText String getSelectedValue();

    /** @deprecated never invoked; please drop */
    @Deprecated(forRemoval = true)
    default @NotNull String getMaxValue() { return ""; }

    default @Nullable Icon getIcon() {
      return null;
    }
  }

  //<editor-fold desc="Deprecated stuff">
  /** @deprecated do not use it */
  @Deprecated(forRemoval = true)
  enum PlatformType {DEFAULT, MAC}

  /** @deprecated implement {@link #getPresentation()} instead */
  @Deprecated(forRemoval = true)
  default @Nullable WidgetPresentation getPresentation(@SuppressWarnings("unused") @NotNull PlatformType type) {
    return null;
  }

  /** @deprecated Use {@link JBUI.CurrentTheme.StatusBar.Widget} border methods */
  @Deprecated
  abstract class WidgetBorder implements Border {
    public static final Border ICON = JBUI.CurrentTheme.StatusBar.Widget.iconBorder();
    public static final Border INSTANCE = JBUI.CurrentTheme.StatusBar.Widget.border();
    public static final Border WIDE = JBUI.CurrentTheme.StatusBar.Widget.border();
  }
  //</editor-fold>
}
