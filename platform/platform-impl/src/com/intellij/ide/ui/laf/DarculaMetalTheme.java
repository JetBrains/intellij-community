
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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



  public String getName() {
    return "Darcula theme";
  }

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

  public ColorUIResource getSeparatorBackground() {
    return getControl();
  }

  public ColorUIResource getSeparatorForeground() {
    return mySeparatorForeground;
  }

  public ColorUIResource getMenuBackground() {
    return lightGray;
  }

  public ColorUIResource getMenuSelectedBackground() {
    return darkBlue;
  }

  public ColorUIResource getMenuSelectedForeground() {
    return white;
  }

  public ColorUIResource getAcceleratorSelectedForeground() {
    return white;
  }

  public ColorUIResource getFocusColor() {
    return new ColorUIResource(Color.black);
  }

  protected ColorUIResource getPrimary1() {
    return primary1;
  }

  protected ColorUIResource getPrimary2() {
    return primary2;
  }

  protected ColorUIResource getPrimary3() {
    return primary3;
  }
}
