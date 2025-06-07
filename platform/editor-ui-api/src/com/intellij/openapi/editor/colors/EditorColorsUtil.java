// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

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
  public static @NotNull EditorColorsScheme getGlobalOrDefaultColorScheme() {
    return getColorSchemeForBackground(null);
  }

  public static @Nullable Color getGlobalOrDefaultColor(@NotNull ColorKey colorKey) {
    Color color = getColorSchemeForBackground(null).getColor(colorKey);
    return color != null? color : colorKey.getDefaultColor();
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF, current editor color scheme and the component background.
   */
  public static @NotNull EditorColorsScheme getColorSchemeForComponent(@Nullable JComponent component) {
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
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(dark1 ? "Darcula" : EditorColorsScheme.getDefaultSchemeName());
      if (scheme != null) {
        return scheme;
      }
    }
    return globalScheme;
  }

  public static @NotNull EditorColorsScheme getColorSchemeForPrinting() {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    return colorsManager.isDarkEditor() ? colorsManager.getDefaultScheme()
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

  public static @NotNull ColorKey createColorKey(@NonNls @NotNull String name, @NotNull Color defaultColor) {
    return ColorKey.createColorKey(name, JBColor.namedColor(name, defaultColor));
  }

  public static @Nullable Color getColor(@Nullable Component component, @NotNull ColorKey key) {
    Function<ColorKey, Color> function = ClientProperty.get(component, ColorKey.FUNCTION_KEY);
    Color color = function == null ? null : function.apply(key);
    return color != null ? color : key.getDefaultColor();
  }
}
