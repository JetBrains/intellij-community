// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.icons.CompositeIcon;
import com.intellij.ui.icons.DarkIconProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBCachingScalableIcon;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static com.intellij.ui.scale.ScaleType.USR_SCALE;

public class LayeredIcon extends JBCachingScalableIcon<LayeredIcon> implements DarkIconProvider, CompositeIcon, IconWithToolTip {
  private static final Logger LOG = Logger.getInstance(LayeredIcon.class);
  private final Icon[] myIcons;
  private Icon[] myScaledIcons;
  private final boolean[] myDisabledLayers;
  private final int[] myHShifts;
  private final int[] myVShifts;

  private int myXShift;
  private int myYShift;

  private int myWidth;
  private int myHeight;

  public static final Icon ADD_WITH_DROPDOWN = new LayeredIcon(AllIcons.General.Add, AllIcons.General.Dropdown);
  public static final Icon EDIT_WITH_DROPDOWN = new LayeredIcon(AllIcons.Actions.Edit, AllIcons.General.Dropdown);
  public static final Icon GEAR_WITH_DROPDOWN = new LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown);

  {
    getScaleContext().addUpdateListener(this::updateSize);
    setAutoUpdateScaleContext(false);
  }

  public LayeredIcon(int layerCount) {
    myIcons = new Icon[layerCount];
    myDisabledLayers = new boolean[layerCount];
    myHShifts = new int[layerCount];
    myVShifts = new int[layerCount];
  }

  public LayeredIcon(Icon @NotNull ... icons) {
    this(icons.length);
    for (int i = 0; i < icons.length; i++) {
      setIcon(icons[i], i);
    }
  }

  protected LayeredIcon(LayeredIcon icon) {
    super(icon);
    myIcons = ArrayUtil.copyOf(icon.myIcons);
    myScaledIcons = null;
    myDisabledLayers = ArrayUtil.copyOf(icon.myDisabledLayers);
    myHShifts = ArrayUtil.copyOf(icon.myHShifts);
    myVShifts = ArrayUtil.copyOf(icon.myVShifts);
    myXShift = icon.myXShift;
    myYShift = icon.myYShift;
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
  }

  @NotNull
  @Override
  public LayeredIcon copy() {
    return new LayeredIcon(this);
  }

  @NotNull
  @Override
  public LayeredIcon deepCopy() {
    LayeredIcon icon = new LayeredIcon(this);
    for (int i = 0; i < icon.myIcons.length; i++) {
      icon.myIcons[i] = IconUtil.copy(icon.myIcons[i], null);
    }
    return icon;
  }

  private Icon @NotNull [] myScaledIcons() {
    if (myScaledIcons != null) {
      return myScaledIcons;
    }
    return myScaledIcons = RowIcon.scaleIcons(myIcons, getScale());
  }

  @NotNull
  @Override
  public LayeredIcon withIconPreScaled(boolean preScaled) {
    super.withIconPreScaled(preScaled);
    updateSize();
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LayeredIcon)) return false;

    final LayeredIcon icon = (LayeredIcon)o;

    if (myHeight != icon.myHeight) return false;
    if (myWidth != icon.myWidth) return false;
    if (myXShift != icon.myXShift) return false;
    if (myYShift != icon.myYShift) return false;
    if (!Arrays.equals(myHShifts, icon.myHShifts)) return false;
    if (!Arrays.equals(myIcons, icon.myIcons)) return false;
    if (!Arrays.equals(myVShifts, icon.myVShifts)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public void setIcon(Icon icon, int layer) {
    setIcon(icon, layer, 0, 0);
  }

  @Override
  public Icon getIcon(int layer) {
    return myIcons[layer];
  }

  @Override
  public int getIconCount() {
    return myIcons.length;
  }

  public Icon @NotNull [] getAllLayers() {
    return myIcons;
  }

  public void setIcon(Icon icon, int layer, int hShift, int vShift) {
    if (icon instanceof LayeredIcon) {
      ((LayeredIcon)icon).checkIHaventIconInsideMe(this);
    }
    myIcons[layer] = icon;
    myScaledIcons = null;
    myHShifts[layer] = hShift;
    myVShifts[layer] = vShift;
    updateSize();
  }

  /**
   *
   * @param constraint is expected to be one of compass-directions or CENTER
   */
  public void setIcon(Icon icon, int layer, @MagicConstant(valuesFromClass = SwingConstants.class) int constraint) {
    int width = getIconWidth();
    int height = getIconHeight();
    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    if (width <= 1 || height <= 1) {
      setIcon(icon, layer);
      return;
    }
    int x;
    int y;
    switch (constraint) {
      case SwingConstants.CENTER:
        x = (width - w) / 2;
        y = (height - h) /2;
        break;
      case SwingConstants.NORTH:
        x = (width - w) / 2;
        y = 0;
        break;
      case SwingConstants.NORTH_EAST:
        x = width - w;
        y = 0;
        break;
      case SwingConstants.EAST:
        x = width - w;
        y = (height - h) / 2;
        break;
      case SwingConstants.SOUTH_EAST:
        x = width - w;
        y = height - h;
        break;
      case SwingConstants.SOUTH:
        x = (width - w) / 2;
        y = height - h;
        break;
      case SwingConstants.SOUTH_WEST:
        x = 0;
        y = height - h;
        break;
      case SwingConstants.WEST:
        x = 0;
        y = (height - h) / 2;
        break;
      case SwingConstants.NORTH_WEST:
        x = 0;
        y = 0;
        break;
      default:
        throw new IllegalArgumentException(
          "The constraint should be one of SwingConstants' compass-directions [1..8] or CENTER [0], actual value is " + constraint);
    }
    setIcon(icon, layer, x, y);
  }

  private void checkIHaventIconInsideMe(Icon icon) {
    LOG.assertTrue(icon != this);
    for (Icon child : myIcons) {
      if (child instanceof LayeredIcon) ((LayeredIcon)child).checkIHaventIconInsideMe(icon);
    }
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    getScaleContext().update();
    Icon[] icons = myScaledIcons();
    for (int i = 0; i < icons.length; i++) {
      Icon icon = icons[i];
      if (icon == null || myDisabledLayers[i]) continue;
      int xOffset = (int)Math.floor(x + scaleVal(myXShift + myHShifts(i), OBJ_SCALE));
      int yOffset = (int)Math.floor(y + scaleVal(myYShift + myVShifts(i), OBJ_SCALE));
      icon.paintIcon(c, g, xOffset, yOffset);
    }
  }

  public boolean isLayerEnabled(int layer) {
    return !myDisabledLayers[layer];
  }

  public void setLayerEnabled(int layer, boolean enabled) {
    if (myDisabledLayers[layer] == enabled) {
      myDisabledLayers[layer] = !enabled;
      clearCachedScaledValue();
    }
  }

  @Override
  public int getIconWidth() {
    getScaleContext().update();
    if (myWidth <= 1) updateSize();

    return (int)Math.ceil(scaleVal(myWidth, OBJ_SCALE));
  }

  @Override
  public int getIconHeight() {
    getScaleContext().update();
    if (myHeight <= 1) updateSize();

    return (int)Math.ceil(scaleVal(myHeight, OBJ_SCALE));
  }

  private int myHShifts(int i) {
    return (int)Math.floor(scaleVal(myHShifts[i], USR_SCALE));
  }

  private int myVShifts(int i) {
    return (int)Math.floor(scaleVal(myVShifts[i], USR_SCALE));
  }

  protected void updateSize() {
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    boolean allIconsAreNull = true;
    for (int i = 0; i < myIcons.length; i++) {
      Icon icon = myIcons[i];
      if (icon == null) continue;
      allIconsAreNull = false;
      int hShift = myHShifts(i);
      int vShift = myVShifts(i);
      minX = Math.min(minX, hShift);
      maxX = Math.max(maxX, hShift + icon.getIconWidth());
      minY = Math.min(minY, vShift);
      maxY = Math.max(maxY, vShift + icon.getIconHeight());
    }
    if (allIconsAreNull) return;
    myWidth = maxX - minX;
    myHeight = maxY - minY;

    if (myIcons.length > 1) {
      myXShift = -minX;
      myYShift = -minY;
    }
  }

  @NotNull
  @Override
  public Icon getDarkIcon(boolean isDark) {
    LayeredIcon newIcon = copy();
    for (int i=0; i<newIcon.myIcons.length; i++) {
      newIcon.myIcons[i] = newIcon.myIcons[i] == null ? null : IconLoader.getDarkIcon(newIcon.myIcons[i], isDark);
    }
    return newIcon;
  }

  public static Icon create(final Icon backgroundIcon, final Icon foregroundIcon) {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(backgroundIcon, 0);
    layeredIcon.setIcon(foregroundIcon, 1);
    return layeredIcon;
  }

  @Override
  public String toString() {
    return "Layered icon "+getIconWidth()+"x"+getIconHeight()+". myIcons=" + Arrays.asList(myIcons);
  }

  @Override
  public String getToolTip(boolean composite) {
    return combineIconTooltips(myIcons);
  }

  @Nullable
  static @NlsContexts.Tooltip String combineIconTooltips(Icon[] icons) {
    // If a layered icon contains only a single non-null layer and other layers are null, its tooltip is not a composite one.
    Icon singleIcon = null;
    for (Icon icon : icons) {
      if (icon != null) {
        if (singleIcon != null) {
          @NlsContexts.Tooltip StringBuilder result = new StringBuilder();
          Set<String> seenTooltips = new HashSet<>();
          buildCompositeTooltip(icons, result, seenTooltips);
          return result.toString();
        }
        singleIcon = icon;
      }
    }
    if (singleIcon != null) {
      return singleIcon instanceof IconWithToolTip ? ((IconWithToolTip) singleIcon).getToolTip(false) : null;
    }
    return null;
  }

  private static void buildCompositeTooltip(Icon[] icons, StringBuilder result, Set<? super String> seenTooltips) {
    for (int i = 0; i < icons.length; i++) {
      // first layer is the actual object (noun), other layers are modifiers (adjectives), so put first object in last position
      Icon icon = i == icons.length - 1 ? icons[0] : icons[i + 1];
      if (icon instanceof LayeredIcon) {
        buildCompositeTooltip(((LayeredIcon) icon).myIcons, result, seenTooltips);
      }
      else if (icon instanceof IconWithToolTip) {
        String toolTip = ((IconWithToolTip)icon).getToolTip(true);
        if (toolTip != null && seenTooltips.add(toolTip)) {
          if (result.length() > 0) {
            result.append(" ");
          }
          result.append(toolTip);
        }
      }
    }
  }
}
