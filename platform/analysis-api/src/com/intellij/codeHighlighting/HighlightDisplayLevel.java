// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.ColorizeProxyIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class HighlightDisplayLevel {
  private static final Map<HighlightSeverity, HighlightDisplayLevel> LEVEL_MAP = new HashMap<>();

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity, @NotNull Icon icon) {
    this(severity, icon, icon);
  }

  private HighlightDisplayLevel(@NotNull HighlightSeverity severity, @NotNull Icon icon, @NotNull Icon outlineIcon) {
    this(severity);
    this.icon = icon;
    this.outlineIcon = outlineIcon;
    LEVEL_MAP.put(this.severity, this);
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity) {
    this.severity = severity;
  }

  public static final HighlightDisplayLevel GENERIC_SERVER_ERROR_OR_WARNING =
    new HighlightDisplayLevel(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                              createIconPair(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING,
                                             AllIcons.General.InspectionsWarning, AllIcons.General.InspectionsWarningEmpty));

  public static final HighlightDisplayLevel ERROR =
    new HighlightDisplayLevel(HighlightSeverity.ERROR,
                              createIconPair(CodeInsightColors.ERRORS_ATTRIBUTES,
                                             AllIcons.General.InspectionsError, AllIcons.General.InspectionsErrorEmpty));

  public static final HighlightDisplayLevel WARNING =
    new HighlightDisplayLevel(HighlightSeverity.WARNING,
                              createIconPair(CodeInsightColors.WARNINGS_ATTRIBUTES,
                                             AllIcons.General.InspectionsWarning, AllIcons.General.InspectionsWarningEmpty));

  private static final TextAttributesKey DO_NOT_SHOW_KEY = TextAttributesKey.createTextAttributesKey("DO_NOT_SHOW");
  public static final HighlightDisplayLevel DO_NOT_SHOW = new HighlightDisplayLevel(HighlightSeverity.INFORMATION, EmptyIcon.ICON_0);
  
  public static final HighlightDisplayLevel CONSIDERATION_ATTRIBUTES = new HighlightDisplayLevel(HighlightSeverity.TEXT_ATTRIBUTES, EmptyIcon.ICON_0);

  /**
   * @deprecated use {@link #WEAK_WARNING} instead
   */
  @Deprecated(forRemoval = true)
  public static final HighlightDisplayLevel INFO = new HighlightDisplayLevel(HighlightSeverity.INFO, createIconByKey(DO_NOT_SHOW_KEY));

  public static final HighlightDisplayLevel WEAK_WARNING =
    new HighlightDisplayLevel(HighlightSeverity.WEAK_WARNING,
                              createIconPair(CodeInsightColors.WEAK_WARNING_ATTRIBUTES,
                                             AllIcons.General.InspectionsWarning, AllIcons.General.InspectionsWarningEmpty));

  public static final HighlightDisplayLevel NON_SWITCHABLE_ERROR = new HighlightDisplayLevel(HighlightSeverity.ERROR) {
    @Override
    public boolean isNonSwitchable() {
      return true;
    }
  };
  public static final HighlightDisplayLevel NON_SWITCHABLE_WARNING = new HighlightDisplayLevel(HighlightSeverity.WARNING) {
    @Override
    public boolean isNonSwitchable() {
      return true;
    }
  };

  private Icon icon = EmptyIcon.ICON_16;
  private Icon outlineIcon = EmptyIcon.ICON_16;
  private final HighlightSeverity severity;

  public static @Nullable HighlightDisplayLevel find(String name) {
    if ("NON_SWITCHABLE_ERROR".equals(name)) return NON_SWITCHABLE_ERROR;
    if ("NON_SWITCHABLE_WARNING".equals(name)) return NON_SWITCHABLE_WARNING;
    for (Map.Entry<HighlightSeverity, HighlightDisplayLevel> entry : LEVEL_MAP.entrySet()) {
      HighlightSeverity severity = entry.getKey();
      HighlightDisplayLevel displayLevel = entry.getValue();
      if (Comparing.strEqual(severity.getName(), name)) {
        return displayLevel;
      }
    }
    return null;
  }

  public static HighlightDisplayLevel find(@NotNull HighlightSeverity severity) {
    return LEVEL_MAP.get(severity);
  }

  public @NonNls String toString() {
    return severity.toString();
  }

  public @NotNull @NonNls String getName() {
    return severity.getName();
  }

  public @NotNull Icon getIcon() {
    return icon;
  }

  public @NotNull Icon getOutlineIcon() {
    return outlineIcon;
  }

  public @NotNull HighlightSeverity getSeverity(){
    return severity;
  }

  public boolean isNonSwitchable() {
    return false;
  }

  public static void registerSeverity(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey key, @Nullable Icon icon) {
    Pair<Icon, Icon> iconPair = icon == null ? createIconByKey(key) : new Pair<>(icon, icon);
    HighlightDisplayLevel level = LEVEL_MAP.get(severity);
    if (level == null) {
      new HighlightDisplayLevel(severity, iconPair.first, iconPair.second);
    }
    else {
      level.icon = iconPair.first;
      level.outlineIcon = iconPair.second;
    }
  }

  public static int getEmptyIconDim() {
    return JBUIScale.scale(14);
  }

  private static Pair<Icon, Icon> createIconByKey(@NotNull TextAttributesKey key) {
    if (StringUtil.containsIgnoreCase(key.getExternalName(), "error")) {
      return createIconPair(key, AllIcons.General.InspectionsError, AllIcons.General.InspectionsErrorEmpty);
    }
    else {
      return createIconPair(key, AllIcons.General.InspectionsWarning, AllIcons.General.InspectionsWarningEmpty);
    }
  }

  private static @NotNull Pair<Icon, Icon> createIconPair(@NotNull TextAttributesKey key, @NotNull Icon first, @NotNull Icon second) {
    return new Pair<>(new ColorizedIcon(key, first), new ColorizedIcon(key, second));
  }

  public static @NotNull Icon createIconByMask(final Color renderColor) {
    return new MyColorIcon(getEmptyIconDim(), renderColor);
  }

  private static final class MyColorIcon extends ColorIcon implements ColoredIcon {
    MyColorIcon(int size, @NotNull Color color) {
      super(size, color);
    }

    @Override
    public Color getColor() {
      return getIconColor();
    }
  }

  public interface ColoredIcon {
    Color getColor();
  }

  private static @Nullable Color getColorFromAttributes(@NotNull TextAttributesKey key) {
    final EditorColorsManager manager = EditorColorsManager.getInstance();
    if (manager != null) {
      TextAttributes attributes = manager.getGlobalScheme().getAttributes(key);
      Color stripe = attributes == null ? null : attributes.getErrorStripeColor();
      if (stripe != null) return stripe;
      if (attributes != null) {
        Color effectColor = attributes.getEffectColor();
        if (effectColor != null) {
          return effectColor;
        }
        Color foregroundColor = attributes.getForegroundColor();
        if (foregroundColor != null) {
          return foregroundColor;
        }
        return attributes.getBackgroundColor();
      }
      return null;
    }
    TextAttributes defaultAttributes = key.getDefaultAttributes();
    if (defaultAttributes == null) {
      defaultAttributes = TextAttributes.ERASE_MARKER;
    }
    return defaultAttributes.getErrorStripeColor();
  }

  private static final class ColorizedIcon extends ColorizeProxyIcon implements ColoredIcon {
    private final TextAttributesKey myKey;

    private ColorizedIcon(@NotNull TextAttributesKey key, @NotNull Icon baseIcon) {
      super(baseIcon);
      myKey = key;
    }

    @Override
    public @NotNull Color getColor() {
      return ObjectUtils.notNull(getColorFromAttributes(myKey), JBColor.GRAY);
    }
  }
}
