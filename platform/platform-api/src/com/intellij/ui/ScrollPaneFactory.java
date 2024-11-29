// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author beg
 */
public final class ScrollPaneFactory implements ScrollPaneConstants {
  private ScrollPaneFactory() { }

  public static @NotNull JScrollPane createScrollPane() {
    return new JBScrollPane();
  }

  public static @NotNull JScrollPane createScrollPane(Component view) {
    return new JBScrollPane(view);
  }

  public static @NotNull JScrollPane createScrollPane(@JdkConstants.VerticalScrollBarPolicy int vsbPolicy,
                                                      @JdkConstants.HorizontalScrollBarPolicy int hsbPolicy) {
    return new JBScrollPane(vsbPolicy, hsbPolicy);
  }

  public static @NotNull JScrollPane createScrollPane(Component view,
                                                      @JdkConstants.VerticalScrollBarPolicy int vsbPolicy,
                                                      @JdkConstants.HorizontalScrollBarPolicy int hsbPolicy) {
    return new JBScrollPane(view, vsbPolicy, hsbPolicy);
  }

  public static @NotNull JScrollPane createScrollPane(Component view,
                                                      @JdkConstants.VerticalScrollBarPolicy int vsbPolicy,
                                                      @JdkConstants.HorizontalScrollBarPolicy int hsbPolicy,
                                                      boolean withoutBorder) {
    JBScrollPane scrollPane = new JBScrollPane(view, vsbPolicy, hsbPolicy);
    if (withoutBorder) {
      setScrollPaneEmptyBorder(scrollPane);
    }
    return scrollPane;
  }

  public static @NotNull JScrollPane createScrollPane(Component view, boolean withoutBorder) {
    JBScrollPane scrollPane = new JBScrollPane(view);
    if (withoutBorder) {
      setScrollPaneEmptyBorder(scrollPane);
    }
    return scrollPane;
  }

  /**
   * Creates a {@link JScrollPane} object for the specified {@code view} and initializes its border.
   *
   * @param view    the component to display in a {@code JScrollPane}'s viewport
   * @param borders the {@link SideBorder}'s flags, which define a {@code JScrollPane}'s border
   * @return new {@code JScrollPane} object
   *
   * @see IdeBorderFactory#createBorder(int)
   */
  public static @NotNull JScrollPane createScrollPane(Component view, @MagicConstant(flagsFromClass = SideBorder.class) int borders) {
    JBScrollPane scrollPane = new JBScrollPane(view);
    scrollPane.setBorder(IdeBorderFactory.createBorder(borders));
    return scrollPane;
  }

  private static void setScrollPaneEmptyBorder(JBScrollPane scrollPane) {
    scrollPane.setBorder(JBUI.Borders.empty()); // set empty border, because setting null doesn't always take effect
    scrollPane.setViewportBorder(JBUI.Borders.empty());
  }
}