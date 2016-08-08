/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeHighlighting;

import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class HighlightDisplayLevel {
  private static final Map<HighlightSeverity, HighlightDisplayLevel> ourMap = new HashMap<>();

  public static final HighlightDisplayLevel GENERIC_SERVER_ERROR_OR_WARNING = new HighlightDisplayLevel(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                                                                                                        createIconByKey(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING));
  public static final HighlightDisplayLevel ERROR = new HighlightDisplayLevel(HighlightSeverity.ERROR, createErrorIcon());

  @NotNull
  private static Icon createErrorIcon() {
    return new SingleColorIcon(CodeInsightColors.ERRORS_ATTRIBUTES) {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        IconUtil.colorize(AllIcons.General.InspectionsError, getColor()).paintIcon(c, g, x, y);
      }
    };
  }

  public static final HighlightDisplayLevel WARNING = new HighlightDisplayLevel(HighlightSeverity.WARNING, createIconByKey(CodeInsightColors.WARNINGS_ATTRIBUTES));
  private static final TextAttributesKey DO_NOT_SHOW_KEY = TextAttributesKey.createTextAttributesKey("DO_NOT_SHOW");
  public static final HighlightDisplayLevel DO_NOT_SHOW = new HighlightDisplayLevel(HighlightSeverity.INFORMATION, createIconByMask(JBColor.gray));
  /**
   * use #WEAK_WARNING instead
   */
  @Deprecated
  public static final HighlightDisplayLevel INFO = new HighlightDisplayLevel(HighlightSeverity.INFO, createIconByKey(DO_NOT_SHOW_KEY));
  public static final HighlightDisplayLevel WEAK_WARNING = new HighlightDisplayLevel(HighlightSeverity.WEAK_WARNING, createIconByKey(CodeInsightColors.WEAK_WARNING_ATTRIBUTES));

  public static final HighlightDisplayLevel NON_SWITCHABLE_ERROR = new HighlightDisplayLevel(HighlightSeverity.ERROR);

  private Icon myIcon;
  private final HighlightSeverity mySeverity;

  @Nullable
  public static HighlightDisplayLevel find(String name) {
    for (Map.Entry<HighlightSeverity, HighlightDisplayLevel> entry : ourMap.entrySet()) {
      HighlightSeverity severity = entry.getKey();
      HighlightDisplayLevel displayLevel = entry.getValue();
      if (Comparing.strEqual(severity.getName(), name)) {
        return displayLevel;
      }
    }
    return null;
  }

  public static HighlightDisplayLevel find(HighlightSeverity severity) {
    return ourMap.get(severity);
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity, @NotNull Icon icon) {
    this(severity);
    myIcon = icon;
    ourMap.put(mySeverity, this);
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity) {
    mySeverity = severity;
  }


  public String toString() {
    return mySeverity.toString();
  }

  @NotNull
  public String getName() {
    return mySeverity.getName();
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public HighlightSeverity getSeverity(){
    return mySeverity;
  }

  public static void registerSeverity(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey key, @Nullable Icon icon) {
    Icon severityIcon = icon != null ? icon : createIconByKey(key);
    final HighlightDisplayLevel level = ourMap.get(severity);
    if (level == null) {
      new HighlightDisplayLevel(severity, severityIcon);
    }
    else {
      level.myIcon = severityIcon;
    }
  }

  public static int getEmptyIconDim() {
    return JBUI.scale(14);
  }

  public static Icon createIconByKey(@NotNull TextAttributesKey key) {
    return new SingleColorIcon(key);
  }

  @NotNull
  public static Icon createIconByMask(final Color renderColor) {
    return new MyColorIcon(getEmptyIconDim(), renderColor);
  }

  private static class MyColorIcon extends ColorIcon implements ColoredIcon {
    public MyColorIcon(int size, @NotNull Color color) {
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

  public static class SingleColorIcon implements Icon, ColoredIcon {
    private final TextAttributesKey myKey;

    public SingleColorIcon(@NotNull TextAttributesKey key) {
      myKey = key;
    }

    @NotNull
    public Color getColor() {
      return ObjectUtils.notNull(getColorInner(), JBColor.GRAY);
    }

    @Nullable
    public Color getColorInner() {
      final EditorColorsManager manager = EditorColorsManager.getInstance();
      if (manager != null) {
        TextAttributes attributes = manager.getGlobalScheme().getAttributes(myKey);
        Color stripe = attributes.getErrorStripeColor();
        if (stripe != null) return stripe;
        return attributes.getEffectColor();
      }
      TextAttributes defaultAttributes = myKey.getDefaultAttributes();
      if (defaultAttributes == null) defaultAttributes = TextAttributes.ERASE_MARKER;
      return defaultAttributes.getErrorStripeColor();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(getColor());
      int shift = JBUI.scale(2);
      int size = JBUI.scale(10);
      g.fillRect(x + shift, y + shift, size, size);
    }

    @Override
    public int getIconWidth() {
      return getEmptyIconDim();
    }

    @Override
    public int getIconHeight() {
      return getEmptyIconDim();
    }
  }
}
