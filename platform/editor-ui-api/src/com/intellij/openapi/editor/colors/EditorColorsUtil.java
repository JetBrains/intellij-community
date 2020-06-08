// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public final class EditorColorsUtil {
  private EditorColorsUtil() {
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF and current editor color scheme.
   */
  @NotNull
  public static EditorColorsScheme getGlobalOrDefaultColorScheme() {
    return getColorSchemeForBackground(null);
  }

  @Nullable
  public static Color getGlobalOrDefaultColor(@NotNull ColorKey colorKey) {
    Color color = getColorSchemeForBackground(null).getColor(colorKey);
    return color != null? color : colorKey.getDefaultColor();
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF, current editor color scheme and the component background.
   */
  @NotNull
  public static EditorColorsScheme getColorSchemeForComponent(@Nullable JComponent component) {
    return getColorSchemeForBackground(component != null ? component.getBackground() : null);
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF, current editor color scheme and background color.
   */
  public static EditorColorsScheme getColorSchemeForBackground(@Nullable Color background) {
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    boolean dark1 = background == null ? StartupUiUtil.isUnderDarcula() : ColorUtil.isDark(background);
    boolean dark2 = ColorUtil.isDark(globalScheme.getDefaultBackground());
    if (dark1 != dark2) {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(dark1 ? "Darcula" : EditorColorsScheme.DEFAULT_SCHEME_NAME);
      if (scheme != null) {
        return scheme;
      }
    }
    return globalScheme;
  }

  @NotNull
  public static EditorColorsScheme getColorSchemeForPrinting() {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    return colorsManager.isDarkEditor() ? colorsManager.getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME)
                                        : colorsManager.getGlobalScheme();
  }

  /**
   * Use this method before showing a hidden component to make sure its LaF and colors are up-to-date.
   * If a component was not reachable via Swing hierarchy and also was not reported via
   * {@link UIUtil#NOT_IN_HIERARCHY_COMPONENTS}, its look-and-feel and colors need to be refreshed.
   *
   * @see com.intellij.util.ComponentTreeEventDispatcher
   * @see UIUtil#NOT_IN_HIERARCHY_COMPONENTS
   */
  public static void updateNotInHierarchyComponentUIAndColors(@Nullable Component component) {
    if (component == null || component.isValid()) return;
    for (Component o : UIUtil.uiTraverser(component).postOrderDfsTraversal()) {
      if (o instanceof JComponent) ((JComponent)o).updateUI();
      if (o instanceof UISettingsListener) ((UISettingsListener)o).uiSettingsChanged(UISettings.getInstance());
      if (o instanceof EditorColorsListener)
        ((EditorColorsListener)o).globalSchemeChange(EditorColorsManager.getInstance().getGlobalScheme());
    }
  }
}
