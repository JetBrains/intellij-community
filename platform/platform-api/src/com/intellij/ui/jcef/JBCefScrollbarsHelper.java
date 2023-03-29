// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.components.ScrollBarPainter;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ide.ui.UISettingsUtils;

import java.awt.*;
import java.util.List;

public final class JBCefScrollbarsHelper {

  private static @Nullable Integer getScrollbarAlpha(ColorKey colorKey) {
    final var contrastElementsKeys = List.of(
      ScrollBarPainter.THUMB_OPAQUE_FOREGROUND,
      ScrollBarPainter.THUMB_OPAQUE_BACKGROUND,
      ScrollBarPainter.THUMB_OPAQUE_HOVERED_FOREGROUND,
      ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND,
      ScrollBarPainter.THUMB_FOREGROUND,
      ScrollBarPainter.THUMB_BACKGROUND,
      ScrollBarPainter.THUMB_HOVERED_FOREGROUND,
      ScrollBarPainter.THUMB_HOVERED_BACKGROUND
    );

    if (!UISettings.getShadowInstance().getUseContrastScrollbars() || !contrastElementsKeys.contains(colorKey)) return null;

    int lightAlpha = SystemInfo.isMac ? 120 : 160;
    int darkAlpha = SystemInfo.isMac ? 255 : 180;
    int alpha = Registry.intValue("contrast.scrollbars.alpha.level");
    if (alpha > 0) {
      return Integer.min(alpha, 255);
    }

    return UIUtil.isUnderDarcula() ? darkAlpha : lightAlpha;
  }

  private static @NotNull String getCssColor(ColorKey key) {
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
    Color color = ObjectUtils.notNull(colorsScheme.getColor(key), key.getDefaultColor());
    double alpha = ObjectUtils.notNull(getScrollbarAlpha(key), color.getAlpha()) / 255.0;

    return String.format("rgba(%d, %d, %d, %f)", color.getRed(), color.getBlue(), color.getBlue(), alpha);
  }

  private static @NotNull String buildCssColor(Color color) {
    return String.format("rgba(%d, %d, %d, %f)", color.getRed(), color.getBlue(), color.getBlue(), color.getAlpha() / 255.0);
  }

  public static @NotNull String buildScrollbarsStyle() {
    var backgroundColor = getCssColor(ScrollBarPainter.BACKGROUND);
    var trackColor = getCssColor(ScrollBarPainter.TRACK_BACKGROUND);
    var trackHoveredColor = getCssColor(ScrollBarPainter.TRACK_HOVERED_BACKGROUND);

    var thumbColor = getCssColor(ScrollBarPainter.THUMB_BACKGROUND);
    var thumbHoveredColor = getCssColor(ScrollBarPainter.THUMB_HOVERED_BACKGROUND);
    var thumbBorderColor = getCssColor(ScrollBarPainter.THUMB_FOREGROUND);

    if (thumbBorderColor.equals(thumbColor)) {
      // See com.intellij.ui.components.ScrollBarPainter#Thumb. In this case we ignore the borders
      thumbBorderColor = buildCssColor(Gray.TRANSPARENT);
    }

    var thumbBorderHoveredColor = getCssColor(ScrollBarPainter.THUMB_HOVERED_FOREGROUND);
    if (thumbBorderHoveredColor.equals(thumbHoveredColor)) {
      // See com.intellij.ui.components.ScrollBarPainter#Thumb. In this case we ignore the borders
      thumbBorderHoveredColor = buildCssColor(Gray.TRANSPARENT);
    }

    var scale = UISettingsUtils.getInstance().getCurrentIdeScale();
    int trackSizePx = (int)(JBCefApp.normalizeScaledSize(SystemInfo.isMac ? 14 : 10) * scale);
    int thumbBorderSizePx = (int)(JBCefApp.normalizeScaledSize(SystemInfo.isMac ? 3 : 1) * scale);
    int thumbRadiusPx = (int)(JBCefApp.normalizeScaledSize(SystemInfo.isMac ? 14 : 0) * scale);

    return
      String.format(
        """
          ::-webkit-scrollbar {
            width: %dpx;
            height: %dpx;
            background-color: %s;
          }
          """, trackSizePx, trackSizePx, backgroundColor) +
      String.format(
        """
          ::-webkit-scrollbar-track {
            background-color: %s;
          }
          """, trackColor) +
      String.format(
        """
          ::-webkit-scrollbar-track:hover {
            background-color: %s;
          }
          """, trackHoveredColor) +
      String.format(
        """
          ::-webkit-scrollbar-thumb {
            background-color: %s;
            border-radius: %dpx;
            border-width: %dpx;
            border-style: solid;
            border-color: %s;
            background-clip: padding-box;
            outline: 1px solid %s;
            outline-offset: -%dpx;
          }
          """, thumbColor, thumbRadiusPx, thumbBorderSizePx, trackColor, thumbBorderColor, thumbBorderSizePx) +
      String.format(
        """
          ::-webkit-scrollbar-thumb:hover {
            background-color: %s;
            border-radius: %dpx;
            border-width: %dpx;
            border-style: solid;
            border-color: %s;
            background-clip: padding-box;
            outline: 1px solid %s;
            outline-offset: -%dpx;
          }
          """, thumbHoveredColor, thumbRadiusPx, thumbBorderSizePx, trackColor, thumbBorderHoveredColor, thumbBorderSizePx) +
      String.format(
        """
          ::-webkit-scrollbar-corner {
            background-color: %s;
          }
          """, backgroundColor) +
      """
        ::-webkit-scrollbar-button {
          display:none;
        }
        """;
  }
}
