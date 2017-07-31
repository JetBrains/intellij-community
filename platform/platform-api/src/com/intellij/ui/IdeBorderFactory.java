/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static final int BORDER_ROUNDNESS = 5;
  public static final int TITLED_BORDER_TOP_INSET = TitledSeparator.TOP_INSET;
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

  @NotNull
  public static RoundedLineBorder createRoundedBorder() {
    return createRoundedBorder(BORDER_ROUNDNESS);
  }

  @NotNull
  public static RoundedLineBorder createRoundedBorder(int arcSize) {
    return new RoundedLineBorder(getBorderColor(), arcSize);
  }

  @NotNull
  public static RoundedLineBorder createRoundedBorder(int arcSize, final int thickness) {
    return new RoundedLineBorder(getBorderColor(), arcSize, thickness);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(JBInsets.create(insets));
  }

  /**
   * @deprecated
   * @see JBUI.Borders#empty()
   */
  public static Border createEmptyBorder() {
    return JBUI.Borders.empty();
  }

  /**
   * @deprecated
   * @see JBUI.Borders#empty(int)
   */
  public static Border createEmptyBorder(int thickness) {
    return JBUI.Borders.empty(thickness);
  }

  /**
   * @deprecated
   * @see JBUI.Borders#empty(int, int, int, int)
   */
  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return JBUI.Borders.empty(top, left, bottom, right);
  }

  public static TitledBorder createTitledBorder(String s) {
    return createTitledBorder(s, true);
  }

  @Deprecated
  public static IdeaTitledBorder createTitledBorder(String title, boolean hasBoldFont, boolean hasIndent, boolean hasSmallFont) {
    return createTitledBorder(title, hasIndent);
  }

  @Deprecated
  public static IdeaTitledBorder createTitledBorder(String title, boolean hasBoldFont, boolean hasIndent, boolean hasSmallFont, Insets insets) {
    return createTitledBorder(title, hasIndent, insets);
  }

  public static IdeaTitledBorder createTitledBorder(String title, boolean hasIndent) {
    Insets insets = new Insets(TITLED_BORDER_TOP_INSET, TITLED_BORDER_LEFT_INSET, TITLED_BORDER_BOTTOM_INSET, TITLED_BORDER_RIGHT_INSET);
    return createTitledBorder(title, hasIndent, insets);
  }

  public static IdeaTitledBorder createTitledBorder(String title, boolean hasIndent, Insets insets) {
    int indent = hasIndent ? TITLED_BORDER_INDENT : 0;
    return new IdeaTitledBorder(title, indent, insets);
  }

  @Deprecated
  // Don't remove, used in TeamCity plugin.
  public static TitledBorder createTitledHeaderBorder(String title) {
    return new IdeaTitledBorder(title, 10, new Insets(5, 0, 10, 0));
  }

  private static Color getBorderColor() {
    return JBColor.border();
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
      return IdeBorderFactory.createTitledBorder(title, true);
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
      return IdeBorderFactory.createTitledBorder(title, false);
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
      return IdeBorderFactory.createTitledBorder(title, false, new Insets(TITLED_BORDER_TOP_INSET,0,0,0));
    }
  }

  public static class PlainSmallWithIndentWithoutInsets {
    private PlainSmallWithIndentWithoutInsets() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, new Insets(TITLED_BORDER_TOP_INSET,0,0,0));
    }
  }
}
