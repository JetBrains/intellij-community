
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaMetalTheme extends DefaultMetalTheme {

  private final ColorUIResource myControlHighlightColor = new ColorUIResource(108, 111, 113);
  private final ColorUIResource myControlDarkShadowColor = new ColorUIResource(39, 42, 44);
  private final ColorUIResource myControlColor = new ColorUIResource(0x3c3f41);
  private static final ColorUIResource white = new ColorUIResource(255, 255, 255);
  private static final ColorUIResource darkBlue = new ColorUIResource(82, 108, 164);
  private static final ColorUIResource lightGray = new ColorUIResource(214, 214, 214);
  private final ColorUIResource mySeparatorForeground = new ColorUIResource(53, 56, 58);

  public static final ColorUIResource primary1 = new ColorUIResource(53, 56, 58);
  private static final ColorUIResource primary2 = new ColorUIResource(91, 135, 206);
  private static final ColorUIResource primary3 = new ColorUIResource(166, 202, 240);



  @Override
  public String getName() {
    return "Darcula theme";
  }

  @Override
  public ColorUIResource getControl() {
    return myControlColor;
  }

  @Override
  public ColorUIResource getControlHighlight() {
    return myControlHighlightColor;
  }

  @Override
  public ColorUIResource getControlDarkShadow() {
    return myControlDarkShadowColor;
  }

  @Override
  public ColorUIResource getSeparatorBackground() {
    return getControl();
  }

  @Override
  public ColorUIResource getSeparatorForeground() {
    return mySeparatorForeground;
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
