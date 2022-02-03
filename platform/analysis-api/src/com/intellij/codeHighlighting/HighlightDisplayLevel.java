// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class HighlightDisplayLevel {
  private static final Map<HighlightSeverity, HighlightDisplayLevel> ourMap = new HashMap<>();

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
  /**
   * @deprecated use {@link #WEAK_WARNING} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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

  private Pair<Icon, Icon> myIconPair = new Pair<>(null, null);
  private final HighlightSeverity mySeverity;

  @Nullable
  public static HighlightDisplayLevel find(String name) {
    if ("NON_SWITCHABLE_ERROR".equals(name)) return NON_SWITCHABLE_ERROR;
    if ("NON_SWITCHABLE_WARNING".equals(name)) return NON_SWITCHABLE_WARNING;
    for (Map.Entry<HighlightSeverity, HighlightDisplayLevel> entry : ourMap.entrySet()) {
      HighlightSeverity severity = entry.getKey();
      HighlightDisplayLevel displayLevel = entry.getValue();
      if (Comparing.strEqual(severity.getName(), name)) {
        return displayLevel;
      }
    }
    return null;
  }

  public static HighlightDisplayLevel find(@NotNull HighlightSeverity severity) {
    return ourMap.get(severity);
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity, @NotNull Icon icon) {
    this(severity, new Pair<>(icon, icon));
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity, Pair<@NotNull Icon, @NotNull Icon> iconPair) {
    this(severity);
    myIconPair = iconPair;
    ourMap.put(mySeverity, this);
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity) {
    mySeverity = severity;
  }


  public @NonNls String toString() {
    return mySeverity.toString();
  }

  @NotNull
  public @NonNls String getName() {
    return mySeverity.getName();
  }

  @NotNull
  public Icon getIcon() {
    return myIconPair.first;
  }

  @NotNull
  public Icon getOutlineIcon() {
    return myIconPair.second;
  }

  @NotNull
  public HighlightSeverity getSeverity(){
    return mySeverity;
  }

  public boolean isNonSwitchable() {
    return false;
  }

  public static void registerSeverity(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey key, @Nullable Icon icon) {
    Pair<Icon, Icon> iconPair = icon != null ? new Pair<> (icon, icon) : createIconByKey(key);
    HighlightDisplayLevel level = ourMap.get(severity);
    if (level == null) {
      new HighlightDisplayLevel(severity, iconPair);
    }
    else {
      level.myIconPair = iconPair;
    }
  }

  public static int getEmptyIconDim() {
    return JBUIScale.scale(14);
  }

  private static Pair<Icon, Icon> createIconByKey(@NotNull TextAttributesKey key) {
    return StringUtil.containsIgnoreCase(key.getExternalName(), "error") ?
           createIconPair(key, AllIcons.General.InspectionsError, AllIcons.General.InspectionsErrorEmpty) :
           createIconPair(key, AllIcons.General.InspectionsWarning, AllIcons.General.InspectionsWarningEmpty);
  }

  private static Pair<Icon, Icon> createIconPair(@NotNull TextAttributesKey key, @NotNull Icon first, @NotNull Icon second) {
    return new Pair<>(new ColorizedIcon(key, first), new ColorizedIcon(key, second));
  }

  @NotNull
  public static Icon createIconByMask(final Color renderColor) {
    return new MyColorIcon(getEmptyIconDim(), renderColor);
  }

  private static class MyColorIcon extends ColorIcon implements ColoredIcon {
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

  @Nullable
  private static Color getColorFromAttributes(@NotNull TextAttributesKey key) {
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
    if (defaultAttributes == null) defaultAttributes = TextAttributes.ERASE_MARKER;
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
