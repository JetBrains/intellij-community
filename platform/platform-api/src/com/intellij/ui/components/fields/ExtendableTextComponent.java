// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.fields;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public interface ExtendableTextComponent {
  String VARIANT = "extendable";

  List<Extension> getExtensions();

  void setExtensions(Extension... extensions);

  void setExtensions(Collection<? extends Extension> extensions);

  void addExtension(@NotNull Extension extension);

  void removeExtension(@NotNull Extension extension);

  /**
   * @see Extension#create(Icon, String, Runnable)
   * @see Extension#create(Icon, Icon, String, Runnable)
   */
  interface Extension {
    Icon getIcon(boolean hovered);

    default int getIconGap() {
      return JBUIScale.scale(5);
    }

    default int getPreferredSpace() {
      Icon icon1 = getIcon(true);
      Icon icon2 = getIcon(false);
      if (icon1 == null && icon2 == null) return 0;
      if (icon1 == null) return getIconGap() + icon2.getIconWidth();
      if (icon2 == null) return getIconGap() + icon1.getIconWidth();
      return getIconGap() + Math.max(icon1.getIconWidth(), icon2.getIconWidth());
    }

    default int getAfterIconOffset() {
      return 0;
    }

    default boolean isIconBeforeText() {
      return false;
    }

    /**
     * Returns null if default button size calculation should be used
     */
    default @Nullable Dimension getButtonSize() {
      return null;
    }

    default Runnable getActionOnClick() {
      return null;
    }

    default boolean isSelected() {
      return false;
    }

    /**
     * @deprecated Use {@link #getActionOnClick()} instead.
     */
    @Deprecated(forRemoval = true)
    default Runnable getActionOnClick(@NotNull InputEvent inputEvent) {
      return getActionOnClick();
    }

    default @NlsContexts.Tooltip String getTooltip() {
      return null;
    }

    static Extension create(@NotNull Icon icon, @NlsContexts.Tooltip String tooltip, Runnable action) {
      return create(icon, icon, tooltip, action);
    }

    static Extension create(@NotNull Icon defaultIcon, @NotNull Icon hoveredIcon, @NlsContexts.Tooltip String tooltip, Runnable action) {
      return new Extension() {
        @Override
        public Icon getIcon(boolean hovered) {
          return hovered ? hoveredIcon : defaultIcon;
        }

        @Override
        public String getTooltip() {
          return tooltip;
        }

        @Override
        public Runnable getActionOnClick() {
          return action;
        }
      };
    }
  }
}
