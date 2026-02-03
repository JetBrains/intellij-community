// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

import static com.intellij.openapi.util.registry.Registry.intValue;

public final class IdeBorderFactory {
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
    return createBorder(getBorderColor(), borders);
  }

  public static Border createBorder(Color color) {
    return createBorder(color, SideBorder.ALL);
  }

  public static Border createBorder(Color color, @MagicConstant(flagsFromClass = SideBorder.class) int borders) {
    return new SideBorder(color, borders);
  }

  public static @NotNull RoundedLineBorder createRoundedBorder() {
    return createRoundedBorder(BORDER_ROUNDNESS);
  }

  public static @NotNull RoundedLineBorder createRoundedBorder(int arcSize) {
    return new RoundedLineBorder(getBorderColor(), arcSize);
  }

  public static @NotNull RoundedLineBorder createRoundedBorder(int arcSize, final int thickness) {
    return new RoundedLineBorder(getBorderColor(), arcSize, thickness);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(JBInsets.create(insets));
  }

  /**
   * @deprecated use {@link JBUI.Borders#empty()}
   */
  @Deprecated
  public static Border createEmptyBorder() {
    return JBUI.Borders.empty();
  }

  /**
   * @deprecated use {@link JBUI.Borders#empty(int, int, int, int)}
   */
  @Deprecated
  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return JBUI.Borders.empty(top, left, bottom, right);
  }

  public static TitledBorder createTitledBorder(@NlsContexts.BorderTitle String s) {
    return createTitledBorder(s, true);
  }

  public static IdeaTitledBorder createTitledBorder(@NlsContexts.BorderTitle String title, boolean hasIndent) {
    int top = Math.max(0, intValue("ide.titled.border.top", TITLED_BORDER_TOP_INSET));
    int left = Math.max(0, intValue("ide.titled.border.left", TITLED_BORDER_LEFT_INSET));
    int right = Math.max(0, intValue("ide.titled.border.right", TITLED_BORDER_RIGHT_INSET));
    int bottom = Math.max(0, intValue("ide.titled.border.bottom", TITLED_BORDER_BOTTOM_INSET));
    @SuppressWarnings("UseDPIAwareInsets")
    Insets insets = new Insets(top, left, bottom, right);
    return createTitledBorder(title, hasIndent, insets);
  }

  public static IdeaTitledBorder createTitledBorder(@NlsContexts.BorderTitle String title, boolean hasIndent, Insets insets) {
    int indent = hasIndent ? Math.max(0, intValue("ide.titled.border.indent", TITLED_BORDER_INDENT)) : 0;
    return new IdeaTitledBorder(title, indent, insets);
  }

  private static Color getBorderColor() {
    return JBColor.border();
  }


  public static final class PlainSmallWithIndent {
    private PlainSmallWithIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  @NlsContexts.BorderTitle String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title);
    }
  }

  public static final class PlainSmallWithoutIndent {
    private PlainSmallWithoutIndent() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  @NlsContexts.BorderTitle String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false);
    }
  }

  public static final class PlainSmallWithoutIndentWithoutInsets {
    private PlainSmallWithoutIndentWithoutInsets() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  @NlsContexts.BorderTitle String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, false, new Insets(TITLED_BORDER_TOP_INSET,0,0,0));
    }
  }

  public static final class PlainSmallWithIndentWithoutInsets {
    private PlainSmallWithIndentWithoutInsets() {
    }

    public static TitledBorder createTitledBorder(Border border,
                                                  @NlsContexts.BorderTitle String title,
                                                  int titleJustification,
                                                  int titlePosition,
                                                  Font titleFont,
                                                  Color titleColor) {
      return IdeBorderFactory.createTitledBorder(title, true, new Insets(TITLED_BORDER_TOP_INSET,0,0,0));
    }
  }
}
