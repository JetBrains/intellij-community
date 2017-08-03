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

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import java.awt.*;

/**
 * @author beg
 */
public class ScrollPaneFactory implements ScrollPaneConstants {
  private ScrollPaneFactory() {
  }

  public static JScrollPane createScrollPane() {
    return new JBScrollPane();
  }

  public static JScrollPane createScrollPane(Component view) {
    return new JBScrollPane(view);
  }

  public static JScrollPane createScrollPane(@JdkConstants.VerticalScrollBarPolicy int vsbPolicy,
                                             @JdkConstants.HorizontalScrollBarPolicy int hsbPolicy) {
    return new JBScrollPane(vsbPolicy, hsbPolicy);
  }

  public static JScrollPane createScrollPane(Component view,
                                             @JdkConstants.VerticalScrollBarPolicy int vsbPolicy,
                                             @JdkConstants.HorizontalScrollBarPolicy int hsbPolicy) {
    return new JBScrollPane(view, vsbPolicy, hsbPolicy);
  }

  public static JScrollPane createScrollPane(Component view, boolean withoutBorder) {
    JBScrollPane scrollPane = new JBScrollPane(view);
    if (withoutBorder) {
      scrollPane.setBorder(JBUI.Borders.empty()); // set empty border, because setting null doesn't always take effect
      scrollPane.setViewportBorder(JBUI.Borders.empty());
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
  public static JScrollPane createScrollPane(Component view, @MagicConstant(flagsFromClass = SideBorder.class) int borders) {
    JBScrollPane scrollPane = new JBScrollPane(view);
    scrollPane.setBorder(IdeBorderFactory.createBorder(borders));
    return scrollPane;
  }
}
