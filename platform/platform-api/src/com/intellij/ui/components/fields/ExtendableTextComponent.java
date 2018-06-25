// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

import java.util.Collection;
import java.util.List;

import static com.intellij.util.ui.JBUI.scale;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public interface ExtendableTextComponent {
  String VARIANT = "extendable";

  List<Extension> getExtensions();

  void setExtensions(Extension... extensions);

  void setExtensions(Collection<Extension> extensions);

  void addExtension(@NotNull Extension extension);

  void removeExtension(@NotNull Extension extension);

  interface Extension {
    Icon getIcon(boolean hovered);

    default int getIconGap() {
      return scale(5);
    }

    default int getPreferredSpace() {
      Icon icon1 = getIcon(true);
      Icon icon2 = getIcon(false);
      if (icon1 == null && icon2 == null) return 0;
      if (icon1 == null) return getIconGap() + icon2.getIconWidth();
      if (icon2 == null) return getIconGap() + icon1.getIconWidth();
      return getIconGap() + Math.max(icon1.getIconWidth(), icon2.getIconWidth());
    }

    default int getBeforeIconOffset() {
      return 0;
    }

    default boolean isIconBeforeText() {
      return false;
    }

    default Runnable getActionOnClick() {
      return null;
    }

    default String getTooltip() {
      return null;
    }

    static Extension create(@NotNull Icon icon, String tooltip, Runnable action) {
      return create(icon, icon, tooltip, action);
    }

    static Extension create(@NotNull Icon defaultIcon, @NotNull Icon hoveredIcon, String tooltip, Runnable action) {
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
