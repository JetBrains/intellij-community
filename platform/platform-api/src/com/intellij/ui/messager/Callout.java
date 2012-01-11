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
package com.intellij.ui.messager;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author kir
 */
public class Callout {

  public static final int NORTH_WEST = 1;
  public static final int NORTH_EAST = 2;
  public static final int SOUTH_WEST = 3;
  public static final int SOUTH_EAST = 4;

  public static void showText(JTree tree, TreePath path, int direction, String text) {
    showText(TreeUtil.getPointForPath(tree, path), direction, text);
  }

  public static void showTextBelow(JComponent component, String text) {
    final RelativePoint calloutPoint = RelativePoint.getSouthWestOf(component);
    calloutPoint.getPoint().x += 5;
    Callout.showText(calloutPoint, Callout.SOUTH_EAST, text);
  }

  public static void showText(RelativePoint aPoint, int direction, String text) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    JLabel label = new JLabel(text);
    label.setHorizontalAlignment(JLabel.CENTER);

    final CalloutComponent callout = new CalloutComponent(label);
    callout.show(direction, aPoint);
  }

  public static void showText(JComponent component, int direction, String text) {
    final RelativePoint point = new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight() / 2));
    showText(point, direction, text);
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void main(String[] args) {
    JFrame frame = new JFrame("Portlet Test");
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setLocation(300, 300);
    frame.setSize(200, 200);

    JPanel pane = new JPanel(new FlowLayout());


    final JButton first = new JButton("North east");
    first.addActionListener(new ActionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void actionPerformed(ActionEvent e) {
        Callout.showText(first, Callout.NORTH_EAST, "North east");
      }
    });
    pane.add(first);

    final JButton second = new JButton("North west");
    second.addActionListener(new ActionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void actionPerformed(ActionEvent e) {
        Callout.showText(second, Callout.NORTH_WEST, "North west");
      }
    });
    pane.add(second);

    final JButton third = new JButton("South east");
    third.addActionListener(new ActionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void actionPerformed(ActionEvent e) {
        Callout.showText(third, Callout.SOUTH_EAST, "South east");
      }
    });
    pane.add(third);

    final JButton fourth = new JButton("South west");
    fourth.addActionListener(new ActionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void actionPerformed(ActionEvent e) {
        Callout.showText(fourth, Callout.SOUTH_WEST, "South west");
      }
    });
    pane.add(fourth);

    frame.getContentPane().setLayout(new GridBagLayout());
    frame.getContentPane().add(pane);

    frame.setVisible(true);
  }
}
