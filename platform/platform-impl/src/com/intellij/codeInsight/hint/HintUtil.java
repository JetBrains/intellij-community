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

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.HintHint;
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

  public static JComponent createInformationLabel(String text) {
    HintHint hintHint = new HintHint().setTextBg(INFORMATION_COLOR).setTextFg(Color.black).setFont(getBoldFont()).setAwtTooltip(true);

    HintLabel label = new HintLabel();
    label.setText(text, hintHint);
    label.setIcon(INFORMATION_ICON);

    if (!hintHint.isAwtTooltip()) {
      label.setBorder(createHintBorder());
      label.setForeground(Color.black);
      label.setFont(getBoldFont());
      label.setBackground(INFORMATION_COLOR);
      label.setOpaque(true);
    }

    return label;
  }

  public static CompoundBorder createHintBorder() {
    return BorderFactory.createCompoundBorder(
      new ColoredSideBorder(Color.white, Color.white, Color.gray, Color.gray, 1),
      BorderFactory.createEmptyBorder(2, 2, 2, 2)
    );
  }

  public static JComponent createInformationLabel(SimpleColoredText text) {
    return createInformationLabel(text, INFORMATION_ICON);
  }

  public static JComponent createQuestionLabel(String text) {
    HintHint hintHint = new HintHint().setTextBg(QUESTION_COLOR).setTextFg(Color.black).setFont(getBoldFont()).setAwtTooltip(true);

    HintLabel label = new HintLabel();
    label.setText(text, hintHint);
    label.setIcon(QUESTION_ICON);

    if (!hintHint.isAwtTooltip()) {
      label.setBorder(createHintBorder());
      label.setForeground(Color.black);
      label.setFont(getBoldFont());
      label.setBackground(QUESTION_COLOR);
      label.setOpaque(true);
    }
    return label;
  }

  public static JComponent createInformationLabel(final SimpleColoredText text, final Icon icon) {
    SimpleColoredComponent  highlighted = new SimpleColoredComponent ();

    highlighted.setIcon(icon);
    highlighted.setBackground(INFORMATION_COLOR);
    highlighted.setForeground(Color.black);
    highlighted.setFont(getBoldFont());
    text.appendToComponent(highlighted);

    HintLabel label = new HintLabel();
    label.setText(highlighted);

    return label;
  }

  public static JComponent createErrorLabel(String text) {
    HintHint hintHint = new HintHint().setTextBg(ERROR_COLOR).setTextFg(Color.black).setFont(getBoldFont()).setAwtTooltip(true);
    HintLabel label = new HintLabel();
    label.setText(text, hintHint);
    label.setIcon(ERROR_ICON);

    if (!hintHint.isAwtTooltip()) {
      label.setBorder(createHintBorder()
      );
      label.setForeground(Color.black);
      label.setFont(getBoldFont());
      label.setBackground(ERROR_COLOR);
      label.setOpaque(true);
    }

    return label;
  }

  private static Font getBoldFont() {
    return UIUtil.getLabelFont().deriveFont(Font.BOLD);
  }

  public static JLabel createAdComponent(final String bottomText) {
    return createAdComponent(bottomText, getDefaultAdComponentBorder());
  }

  public static EmptyBorder getDefaultAdComponentBorder() {
    return new EmptyBorder(1, 2, 1, 2);
  }

  public static JLabel createAdComponent(final String bottomText, final Border border) {
    return createAdComponent(bottomText, border, SwingUtilities.LEFT);
  }

  public static JLabel createAdComponent(final String bottomText, final Border border, int alignment) {
    JLabel label = new JLabel();
    label.setText(bottomText);
    label.setHorizontalAlignment(alignment);
    label.setFont(label.getFont().deriveFont((float)(label.getFont().getSize() - 2)));
    if (bottomText != null) {
      label.setBorder(border);
    }
    return label;
  }

  private static class HintLabel extends JPanel {

    private JEditorPane myPane;
    private SimpleColoredComponent myColored;
    private JLabel myIcon;

    private HintLabel() {
      setLayout(new BorderLayout());
    }


    public void setText(SimpleColoredComponent colored) {
      clearText();

      myColored = colored;
      add(myColored, BorderLayout.CENTER);

      setOpaque(true);
      setBackground(colored.getBackground());

      revalidate();
      repaint();
    }

    public void setText(String s, HintHint hintHint) {
      clearText();

      if (s != null) {
        myPane = IdeTooltipManager.initPane(s, hintHint, null);
        add(myPane, BorderLayout.CENTER);
      }

      setOpaque(true);
      setBackground(hintHint.getTextBackground());

      revalidate();
      repaint();
    }

    private void clearText() {
      if (myPane != null) {
        remove(myPane);
        myPane = null;
      }

      if (myColored != null) {
        remove(myColored);
        myColored = null;
      }
    }

    public void setIcon(Icon icon) {
      if (myIcon != null) {
        remove(myIcon);
      }

      myIcon = new JLabel(icon, JLabel.CENTER);
      myIcon.setVerticalAlignment(JLabel.TOP);

      add(myIcon, BorderLayout.WEST);

      revalidate();
      repaint();
    }

    @Override
    public String toString() {
      return "Hint: text='" + (myPane != null ? myPane.getText() : "") + "'";
    }

  }
}