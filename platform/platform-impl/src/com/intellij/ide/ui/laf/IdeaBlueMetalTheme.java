// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ui.UIBundle;

import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;

public final class IdeaBlueMetalTheme extends DefaultMetalTheme {
  @Override
  public String getName() {
    return UIBundle.message("idea.blue.metal.theme.name");
  }

  private static final ColorUIResource darkGray = new ColorUIResource(132, 130, 132);
  private static final ColorUIResource white = new ColorUIResource(255, 255, 255);
  private static final ColorUIResource darkBlue = new ColorUIResource(82, 108, 164);
//  private static ColorUIResource lightGray = new ColorUIResource(214, 211, 206);
  private static final ColorUIResource lightGray = new ColorUIResource(214, 214, 214);

  @Override
  public ColorUIResource getControl() {
    return lightGray;
  }

  @Override
  public ColorUIResource getSeparatorBackground() {
    return white;
  }

  @Override
  public ColorUIResource getSeparatorForeground() {
    return darkGray;
  }

  @Override
  public ColorUIResource getMenuBackground() {
    return lightGray;
  }

  @Override
  public ColorUIResource getMenuSelectedBackground() {
    return darkBlue;
  }

  @Override
  public ColorUIResource getMenuSelectedForeground() {
    return white;
  }

  @Override
  public ColorUIResource getAcceleratorSelectedForeground() {
    return white;
  }

  public static final ColorUIResource primary1 = new ColorUIResource(10, 36, 106);
  private static final ColorUIResource primary2 = new ColorUIResource(91, 135, 206);
  private static final ColorUIResource primary3 = new ColorUIResource(166, 202, 240);

  @Override
  public ColorUIResource getFocusColor() {
    return new ColorUIResource(Color.black);
  }

  @Override
  protected ColorUIResource getPrimary1() {
    return primary1;
  }

  @Override
  protected ColorUIResource getPrimary2() {
    return primary2;
  }

  @Override
  protected ColorUIResource getPrimary3() {
    return primary3;
  }
}
