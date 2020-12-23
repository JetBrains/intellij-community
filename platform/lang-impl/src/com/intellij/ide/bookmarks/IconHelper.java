// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.IconWrapperWithToolTip;
import com.intellij.util.ui.RegionPaintIcon;
import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Component;

final class IconHelper {
  private static final String MNEMONICS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final char DEFAULT = 0;
  private static final Icon NORMAL_ICON = createIcon(false, DEFAULT);
  private static final Icon GUTTER_ICON = createIcon(true, DEFAULT);
  private static final Icon[] NORMAL_ICONS = new Icon[MNEMONICS.length()];
  private static final Icon[] GUTTER_ICONS = new Icon[MNEMONICS.length()];

  static @NotNull Icon getIcon() {
    return getIcon(false, DEFAULT);
  }

  static @NotNull Icon getIcon(char mnemonic) {
    return getIcon(false, mnemonic);
  }

  static @NotNull Icon getGutterIcon(char mnemonic) {
    return getIcon(true, mnemonic);
  }

  private static @NotNull Icon getIcon(boolean gutter, char mnemonic) {
    if (mnemonic == DEFAULT) return gutter ? GUTTER_ICON : NORMAL_ICON;
    int index = MNEMONICS.indexOf(mnemonic);
    if (index < 0) return createIcon(gutter, mnemonic);
    Icon[] cache = gutter ? GUTTER_ICONS : NORMAL_ICONS;
    if (cache[index] == null) cache[index] = createIcon(gutter, mnemonic);
    return cache[index];
  }

  private static @NotNull Icon createIcon(boolean gutter, char mnemonic) {
    RegionPainter<Component> painter = mnemonic == DEFAULT ? new BookmarkPainter() : new MnemonicPainter(mnemonic);
    int size = gutter ? 12 : 16;
    int insets = gutter ? 0 : 1;
    Icon icon = new RegionPaintIcon(size, size, insets, painter).withIconPreScaled(false);
    return new IconWrapperWithToolTip(icon, IdeBundle.messagePointer("tooltip.bookmarked"));
  }
}
