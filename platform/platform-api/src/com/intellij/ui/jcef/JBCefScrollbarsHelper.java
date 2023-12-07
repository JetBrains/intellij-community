// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.ScrollBarPainter;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Provides styles and scripts required to style JCEF browser's scrollbars.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/jcef.html">Embedded Browser (JCEF) (IntelliJ Platform Docs)</a>
 */
public final class JBCefScrollbarsHelper {
  private static final LazyInitializer.LazyValue<@NotNull String> OVERLAY_SCROLLBARS_CSS = LazyInitializer.create(() -> {
    return readResource("resources/overlayscrollbars/overlayscrollbars.css");
  });

  private static final LazyInitializer.LazyValue<@NotNull String> OVERLAY_SCROLLBARS_JS = LazyInitializer.create(() -> {
    return readResource("resources/overlayscrollbars/overlayscrollbars.browser.es6.js");
  });

  private static final String TRANSPARENT_CSS_COLOR = "rgba(0, 0, 0, 0.0)";

  /**
   * Returns the content of the OverlayScrollbars library's CSS.
   * <p>
   * It must be included on the page to use <a href="https://kingsora.github.io/OverlayScrollbars/">OverlayScrollbars</a>.
   *
   * @see JBCefScrollbarsHelper#getOverlayScrollbarsSourceJS()
   * @see JBCefScrollbarsHelper#buildScrollbarsStyle()
   */
  public static @NotNull String getOverlayScrollbarsSourceCSS() {
    return OVERLAY_SCROLLBARS_CSS.get();
  }

  /**
   * Returns the content of the OverlayScrollbars library's JavaScript.
   * <p>
   * It must be included on the page to use <a href="https://kingsora.github.io/OverlayScrollbars/">OverlayScrollbars</a>.
   * After loading the script, it must be installed on the page:
   * <pre><code>
   * const overlayScrollbars = OverlayScrollbars(document.getElementById('view_port'), {});
   * </code></pre>
   * The example above installs the scrollbar on the {@code view_port} element.
   * To install it for other elements or use more advanced options, see the OverlayScrollbars documentation.
   *
   * @see JBCefScrollbarsHelper#getOverlayScrollbarsSourceCSS()
   * @see JBCefScrollbarsHelper#buildScrollbarsStyle()
   */
  public static @NotNull String getOverlayScrollbarsSourceJS() {
    return OVERLAY_SCROLLBARS_JS.get();
  }

  /**
   * Returns the styles adapting <a href="https://kingsora.github.io/OverlayScrollbars/">OverlayScrollbars</a> look and feel to the IDE.
   * It must be included in the page styles.
   *
   * @see JBCefScrollbarsHelper#getOverlayScrollbarsSourceCSS()
   * @see JBCefScrollbarsHelper#getOverlayScrollbarsSourceJS()
   */
  public static @NotNull String buildScrollbarsStyle() {
    final String transparent = "rgba(0, 0, 0, 0)";

    var thumbColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND);
    var thumbHoveredColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND);
    var thumbBorderColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_FOREGROUND);

    if (thumbBorderColor.equals(thumbColor)) {
      // See com.intellij.ui.components.ScrollBarPainter#Thumb. In this case we ignore the borders
      thumbBorderColor = TRANSPARENT_CSS_COLOR;
    }

    var thumbBorderHoveredColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_HOVERED_FOREGROUND);
    if (thumbBorderHoveredColor.equals(thumbHoveredColor)) {
      // See com.intellij.ui.components.ScrollBarPainter#Thumb. In this case we ignore the borders
      thumbBorderHoveredColor = TRANSPARENT_CSS_COLOR;
    }

    int trackSizePx = getTrackSizePx();
    int thumbPaddingPx = getThumbPaddingPx();
    int thumbRadiusPx = getThumbRadiusPx();

    return
      String.format(
        Locale.ROOT,
        """
          ::-webkit-scrollbar {
            width: %dpx;
            height: %dpx;
            background-color: %s;
          }
          """, trackSizePx, trackSizePx, transparent) +
      String.format(
        Locale.ROOT,
        """
          ::-webkit-scrollbar-track {
            background-color: %s;
          }
          """, transparent) +
      String.format(
        Locale.ROOT,
        """
          ::-webkit-scrollbar-track:hover {
            background-color: %s;
          }
          """, transparent) +
      String.format(
        Locale.ROOT,
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
          """, thumbColor, thumbRadiusPx, thumbPaddingPx, transparent, thumbBorderColor, thumbPaddingPx) +
      String.format(
        Locale.ROOT,
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
          """, thumbHoveredColor, thumbRadiusPx, thumbPaddingPx, transparent, thumbBorderHoveredColor, thumbPaddingPx) +
      String.format(
        Locale.ROOT,
        """
          ::-webkit-scrollbar-corner {
            background-color: %s;
          }
          """, transparent) +
      """
        ::-webkit-scrollbar-button {
          display:none;
        }
        """;
  }

  /**
   * Returns the styles adapting scrollbars look and feel to the IDE.
   * It doesn't use any external library.
   * It must be included in the page styles.
   */
  public static @NotNull String getOverlayScrollbarStyle() {
    var trackColor = getCssColor(ScrollBarPainter.TRACK_OPAQUE_BACKGROUND);
    var trackHoveredColor = getCssColor(ScrollBarPainter.TRACK_OPAQUE_HOVERED_BACKGROUND);

    var thumbColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND);
    var thumbHoveredColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND);
    var thumbBorderColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_FOREGROUND);
    var thumbBorderHoveredColor = getCssColor(ScrollBarPainter.THUMB_OPAQUE_HOVERED_FOREGROUND);

    if (thumbBorderColor.equals(thumbColor)) {
      // See com.intellij.ui.components.ScrollBarPainter#Thumb. In this case we ignore the borders
      thumbBorderColor = TRANSPARENT_CSS_COLOR;
    }

    if (thumbBorderHoveredColor.equals(thumbHoveredColor)) {
      // See com.intellij.ui.components.ScrollBarPainter#Thumb. In this case we ignore the borders
      thumbBorderHoveredColor = TRANSPARENT_CSS_COLOR;
    }

    final int thumbBorderWidthPx = 1;
    int trackSizePx = getTrackSizePx();
    int thumbPaddingPx = getThumbPaddingPx();
    int thumbRadiusPx = getThumbRadiusPx();
    int thumbSizePercent = 100;

    return ".os-scrollbar {\n" +
           "  --os-size: " + trackSizePx + "px;\n" +
           "  --os-padding-perpendicular: " + (thumbPaddingPx + thumbBorderWidthPx) + "px;\n" +
           "  --os-handle-border-radius: " + thumbRadiusPx + "px;\n" +
           "  --os-track-border-radius: 0;" +

           "  --os-track-bg: " + trackColor + ";\n" +
           "  --os-track-bg-active: " + trackColor + ";\n" +
           "  --os-track-bg-hover: " + trackHoveredColor + ";\n" +

           "  --os-handle-bg: " + thumbColor + ";\n" +
           "  --os-handle-bg-active: " + thumbColor + ";\n" +
           "  --os-handle-bg-hover: " + thumbHoveredColor + ";\n" +
           "  --os-handle-perpendicular-size: " + thumbSizePercent + "%;\n" +
           "  --os-handle-perpendicular-size-hover: " + thumbSizePercent + "%;\n" +
           "  --os-handle-perpendicular-size-active: " + thumbSizePercent + "%;\n" +
           "}\n" +
           ".os-scrollbar-handle {" +
           "  outline: " + thumbBorderWidthPx + "px solid " + thumbBorderColor + ";\n" +
           "}" +
           ".os-scrollbar-handle:hover {" +
           "  outline: " + thumbBorderWidthPx + "px solid " + thumbBorderHoveredColor + ";\n" +
           "}" +
           ".os-scrollbar-handle:active {" +
           "  outline: " + thumbBorderWidthPx + "px solid " + thumbBorderHoveredColor + ";\n" +
           "}"
      ;
  }

  private static int getTrackSizePx() {
    return (int)(JBCefApp.normalizeScaledSize(SystemInfo.isMac ? 14 : 10) * UISettingsUtils.getInstance().getCurrentIdeScale());
  }

  private static int getThumbPaddingPx() {
    return (int)(JBCefApp.normalizeScaledSize(SystemInfo.isMac ? 3 : 1) * UISettingsUtils.getInstance().getCurrentIdeScale());
  }

  private static int getThumbRadiusPx() {
    return (int)(JBCefApp.normalizeScaledSize(SystemInfo.isMac ? 7 : 0) * UISettingsUtils.getInstance().getCurrentIdeScale());
  }


  private static @Nullable Integer getScrollbarAlpha(ColorKey colorKey) {
    if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred() || !UISettings.getInstance().getUseContrastScrollbars()) {
      return null;
    }

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

    if (!contrastElementsKeys.contains(colorKey)) {
      return null;
    }

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

    return String.format(Locale.ROOT, "rgba(%d, %d, %d, %f)", color.getRed(), color.getGreen(), color.getBlue(), alpha);
  }

  private static @NotNull String readResource(@NotNull String path) {
    try {
      return new String(FileUtil.loadBytes(Objects.requireNonNull(
        JBCefApp.class.getResourceAsStream(path))), StandardCharsets.UTF_8);
    }
    catch (IOException | NullPointerException e) {
      Logger.getInstance(JBCefScrollbarsHelper.class).error("couldn't find " + path, e);
    }

    return "";
  }
}
