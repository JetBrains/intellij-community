/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static final int BORDER_ROUNDNESS = 5;

  private IdeBorderFactory() {
  }

  public static Border createBorder() {
    return createBorder(SideBorder.ALL);
  }

  public static Border createBorder(int borders) {
    return new SideBorder(getBorderColor(), borders);
  }

  public static Border createRoundedBorder() {
    return new RoundedLineBorder(getBorderColor(), BORDER_ROUNDNESS);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(insets);
  }

  public static Border createEmptyBorder(int thickness) {
    return new EmptyBorder(thickness, thickness, thickness, thickness);
  }

  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return new EmptyBorder(top, left, bottom, right);
  }

  public static TitledBorder createTitledBorder(String s) {
    return createTitledBorder(s, false, true, true);
  }

  public static IdeaTitledBorder createTitledBorder(String title, boolean hasBoldFont, boolean hasIndent, boolean hasSmallFont) {
    Font font = UIUtil.getBorderFont();
    if (hasBoldFont) {
      font = font.deriveFont(Font.BOLD);
    }
    if (hasSmallFont) {
      font = UIUtil.getFont(UIUtil.FontSize.SMALL, font);
    }
    int indent = hasIndent ? (hasBoldFont ? 18 : 15) : 0;
    Insets insets = hasBoldFont ? new Insets(5,0,10,0) : new Insets(3,0,6,0);
    return new IdeaTitledBorder(title, font, UIUtil.getBorderColor(), indent, 1, insets);
  }

  @Deprecated
  // Don't remove, used in TeamCity plugin.
  public static TitledBorder createTitledHeaderBorder(String title) {
    return new IdeaTitledBorder(title, UIUtil.getBorderFont().deriveFont(Font.BOLD), UIUtil.getBorderColor(), 10, 1, new Insets(5,0,10,0));
  }

  private static Color getBorderColor() {
    return UIUtil.getBorderColor();
  }



  public static class SimpleWithIndent {
    private SimpleWithIndent() {
    }
    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification, int titlePosition, Font titleFont, Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, true, true);
    }
  }

  public static class SimpleWithoutIndent {
    private SimpleWithoutIndent() {
    }
    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification, int titlePosition, Font titleFont, Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, false, true);
    }
  }

  public static class BoldWithIndent {
    private BoldWithIndent() {
    }
    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification, int titlePosition, Font titleFont, Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, true, true);
    }
  }

  public static class BoldWithoutIndent {
    private BoldWithoutIndent() {
    }
    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification, int titlePosition, Font titleFont, Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, false, true);
    }
  }

}
