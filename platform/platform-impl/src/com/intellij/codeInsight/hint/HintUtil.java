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
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SideBorder2;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HintUtil {
  public static final Color INFORMATION_COLOR = new Color(253, 254, 226);
  public static final Color QUESTION_COLOR = new Color(181, 208, 251);
  private static final Color ERROR_COLOR = new Color(255, 220, 220);

  private static final Icon INFORMATION_ICON = null;
  private static final Icon QUESTION_ICON = IconLoader.getIcon("/actions/help.png");
  private static final Icon ERROR_ICON = null;

  public static final Color QUESTION_UNDERSCORE_COLOR = Color.black;

  private HintUtil() {
  }

  public static JLabel createInformationLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(INFORMATION_ICON);
    label.setBorder(createHintBorder());
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(INFORMATION_COLOR);
    label.setOpaque(true);

    return label;
  }

  public static CompoundBorder createHintBorder() {
    return BorderFactory.createCompoundBorder(
      new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
      BorderFactory.createEmptyBorder(2, 2, 2, 2)
    );
  }

  public static JComponent createInformationLabel(SimpleColoredText text) {
    return createInformationLabel(text, INFORMATION_ICON);
  }

  public static JLabel createQuestionLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(QUESTION_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(createHintBorder());
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(QUESTION_COLOR);
    label.setOpaque(true);
    return label;
  }

  public static JComponent createInformationLabel(final SimpleColoredText text, final Icon icon) {
    SimpleColoredComponent  highlighted = new SimpleColoredComponent ();

    highlighted.setIcon(icon);
    highlighted.setBackground(INFORMATION_COLOR);
    highlighted.setForeground(Color.black);
    highlighted.setFont(getBoldFont());
    text.appendToComponent(highlighted);

    Box box = Box.createHorizontalBox();
    box.setBorder(
      new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1)
    );
    box.setForeground(Color.black);
    box.setBackground(INFORMATION_COLOR);
    box.add(highlighted);
    box.setOpaque(true);
    return box;
  }

  public static JLabel createErrorLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text.replaceAll("\n"," "));
    label.setIcon(ERROR_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(createHintBorder()
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(ERROR_COLOR);
    label.setOpaque(true);
    return label;
  }

  private static Font getBoldFont() {
    return UIUtil.getLabelFont().deriveFont(Font.BOLD);
  }

  public static JLabel createAdComponent(final String bottomText) {
    return createAdComponent(bottomText, new EmptyBorder(1, 2, 1, 2));
  }

  public static JLabel createAdComponent(final String bottomText, final Border border) {
    JLabel label = new JLabel();
    label.setText(bottomText);
    label.setFont(label.getFont().deriveFont((float)(label.getFont().getSize() - 2)));
    if (bottomText != null) {
      label.setBorder(border);
    }
    return label;
  }

  private static class HintLabel extends JLabel {
    public void setText(String s) {
      if (s == null) {
        super.setText(null);
      }
      else {
        final int length = s.length();

        final String alignedText;
        if (length < 100 && !s.contains("\n")) {
          alignedText = s;
        } else {
          alignedText = s.replaceAll("(\n)|(\r\n)", " \n ");
        }
        super.setText(" " + alignedText + " ");
      }
    }

    @Override
    public String toString() {
      return "Hint: '" + getText() + "'";
    }
  }
}