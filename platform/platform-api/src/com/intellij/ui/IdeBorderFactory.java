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
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static final int BORDER_ROUNDNESS = 5;
  public static final int TITLED_BORDER_TOP_INSET = 7;
  public static final int TITLED_BORDER_LEFT_INSET = 0;
  public static final int TITLED_BORDER_BOTTOM_INSET = 10;
  public static final int TITLED_BORDER_RIGHT_INSET = 0;
  public static final int TITLED_BORDER_INDENT = 20;

  private IdeBorderFactory() {
  }

  public static Border createBorder() {
    return createBorder(SideBorder.ALL);
  }

  public static Border createBorder(@MagicConstant(flagsFromClass = SideBorder.class) int borders) {
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
    Insets insets = new Insets(TITLED_BORDER_TOP_INSET, TITLED_BORDER_LEFT_INSET, TITLED_BORDER_BOTTOM_INSET, TITLED_BORDER_RIGHT_INSET);
    return createTitledBorder(title, hasBoldFont, hasIndent, hasSmallFont, insets);
  }

    public static IdeaTitledBorder createTitledBorder(String title, boolean hasBoldFont, boolean hasIndent, boolean hasSmallFont, Insets insets) {
      int indent = hasIndent ? TITLED_BORDER_INDENT : 0;
      return new IdeaTitledBorder(title, hasBoldFont, hasSmallFont, indent, insets);
    }

  @Deprecated
  // Don't remove, used in TeamCity plugin.
  public static TitledBorder createTitledHeaderBorder(String title) {
    return new IdeaTitledBorder(title, false, true, 10,
                                new Insets(5, 0, 10, 0));
  }

  private static Color getBorderColor() {
    return UIUtil.getBorderColor();
  }


  public static class PlainSmallWithIndent {
    private PlainSmallWithIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, true, true);
    }
  }

  public static class PlainSmallWithoutIndent {
    private PlainSmallWithoutIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, false, true);
    }
  }

  public static class BoldSmallWithIndent {
    private BoldSmallWithIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, true, true);
    }
  }

  public static class BoldSmallWithoutIndent {
    private BoldSmallWithoutIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, false, true);
    }
  }

  public static class PlainLargeWithIndent {
    private PlainLargeWithIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, true, false);
    }
  }

  public static class PlainLargeWithoutIndent {
    private PlainLargeWithoutIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, false, false);
    }
  }

  public static class BoldLargeWithIndent {
    private BoldLargeWithIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, true, false);
    }
  }

  public static class BoldLargeWithoutIndent {
    private BoldLargeWithoutIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, false, false);
    }
  }

  public static class PlainSmallWithoutIndentWithoutInsets {
    private PlainSmallWithoutIndentWithoutInsets() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, false, true, new Insets(5,0,0,0));
    }
  }

}
