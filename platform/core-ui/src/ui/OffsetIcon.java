// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBCachingScalableIcon;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static java.lang.Math.ceil;

public final class OffsetIcon extends JBCachingScalableIcon<OffsetIcon> {

  public static final int REGULAR_OFFSET = 20;

  private int myWidth;
  private int myHeight;

  private final int myOffset;
  private final Icon myIcon;
  private Icon myScaledIcon;
  private int myScaledOffset;

  {
    getScaleContext().addUpdateListener(this::updateSize);
    setAutoUpdateScaleContext(false);
  }

  @Contract("null->null;!null->!null")
  public static Icon getOriginalIcon(Icon icon) {
    return icon instanceof OffsetIcon ? ((OffsetIcon)icon).myIcon : icon;
  }

  public OffsetIcon(@NotNull Icon icon) {
    this(REGULAR_OFFSET, icon);
  }

  public OffsetIcon(int offset, @NotNull Icon icon) {
    myOffset = offset;
    myIcon = icon;
    updateSize();
  }

  private OffsetIcon(@NotNull OffsetIcon icon) {
    super(icon);
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
    myOffset = icon.myOffset;
    myIcon = icon.myIcon;
    myScaledIcon = null;
    myScaledOffset = icon.myScaledOffset;
  }

  @NotNull
  @Override
  public OffsetIcon copy() {
    return new OffsetIcon(this);
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  public int hashCode() {
    return myOffset + myIcon.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj instanceof OffsetIcon) {
      OffsetIcon icon = (OffsetIcon)obj;
      return icon.myOffset == myOffset && Objects.equals(icon.myIcon, myIcon);
    }
    return false;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    getScaleContext().update();
    if (myScaledIcon == null) {
      float scale = getScale();
      myScaledIcon = scale == 1f ? myIcon : IconUtil.scale(myIcon, null, scale);
    }
    myScaledIcon.paintIcon(c, g, myScaledOffset + x, y);
  }

  @Override
  public int getIconWidth() {
    getScaleContext().update();
    return (int)ceil(scaleVal(myWidth, OBJ_SCALE)) + myScaledOffset;
  }

  @Override
  public int getIconHeight() {
    getScaleContext().update();
    return (int)ceil(scaleVal(myHeight, OBJ_SCALE));
  }

  private void updateSize() {
    myWidth = myIcon.getIconWidth();
    myHeight = myIcon.getIconHeight();
    myScaledOffset = (int)ceil(scaleVal(myOffset));
  }

  @Override
  public String toString() {
    return "OffsetIcon: offset=" + myOffset + "; icon=" + myIcon;
  }
}
