// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseListener;

import static com.intellij.openapi.editor.colors.EditorColorsUtil.getGlobalOrDefaultColor;
import static com.intellij.util.ObjectUtils.notNull;

public class HintUtil {
  /** @deprecated use getInformationColor() */
  @Deprecated
  public static final Color INFORMATION_COLOR = new JBColor(0xF7F7F7, 0x4B4D4D);
  private static final Color INFORMATION_BORDER = new JBColor(0xE6E6E6, 0x5C5C5C);
  /** @deprecated use getQuestionColor() */
  @Deprecated
  public static final Color QUESTION_COLOR = new JBColor(new Color(181, 208, 251), new Color(55, 108, 137));
  /** @deprecated use getErrorColor() */
  @Deprecated
  public static final Color ERROR_COLOR = new JBColor(new Color(255, 220, 220), new Color(0x781732));

  public static final ColorKey INFORMATION_COLOR_KEY = ColorKey.createColorKey("INFORMATION_HINT", INFORMATION_COLOR);
  public static final ColorKey QUESTION_COLOR_KEY = ColorKey.createColorKey("QUESTION_HINT", QUESTION_COLOR);
  public static final ColorKey ERROR_COLOR_KEY = ColorKey.createColorKey("ERROR_HINT", ERROR_COLOR);

  public static final Color QUESTION_UNDERSCORE_COLOR = JBColor.foreground();

  private HintUtil() {
  }

  @NotNull
  public static Color getInformationColor() {
    return notNull(getGlobalOrDefaultColor(INFORMATION_COLOR_KEY), INFORMATION_COLOR_KEY.getDefaultColor());
  }

  @NotNull
  public static Color getQuestionColor() {
    return notNull(getGlobalOrDefaultColor(QUESTION_COLOR_KEY), QUESTION_COLOR_KEY.getDefaultColor());
  }

  @NotNull
  public static Color getErrorColor() {
    return notNull(getGlobalOrDefaultColor(ERROR_COLOR_KEY), ERROR_COLOR_KEY.getDefaultColor());
  }

  public static JComponent createInformationLabel(@NotNull String text) {
    return createInformationLabel(text, null, null, null);
  }

  public static JComponent createInformationLabel(@NotNull String text,
                                                  @Nullable HyperlinkListener hyperlinkListener,
                                                  @Nullable MouseListener mouseListener,
                                                  @Nullable Ref<Consumer<String>> updatedTextConsumer) {
    HintHint hintHint = getInformationHint();
    HintLabel label = createLabel(text, null, hintHint.getTextBackground(), hintHint);
    configureLabel(label, hyperlinkListener, mouseListener, updatedTextConsumer);
    return label;
  }

  @NotNull
  public static HintHint getInformationHint() {
    //noinspection UseJBColor
    return new HintHint()
      .setBorderColor(INFORMATION_BORDER)
      .setTextBg(getInformationColor())
      .setTextFg(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : Color.black)
      .setFont(getBoldFont())
      .setAwtTooltip(true);
  }

  public static CompoundBorder createHintBorder() {
    //noinspection UseJBColor
    return BorderFactory.createCompoundBorder(
      new ColoredSideBorder(Color.white, Color.white, Color.gray, Color.gray, 1),
      BorderFactory.createEmptyBorder(2, 2, 2, 2)
    );
  }

  @NotNull
  public static JComponent createInformationLabel(SimpleColoredText text) {
    return createInformationLabel(text, null);
  }

  public static JComponent createQuestionLabel(String text) {
    final Icon icon = AllIcons.General.Help_small;
    return createQuestionLabel(text, icon);
  }

  public static JComponent createQuestionLabel(String text, Icon icon) {
    Color bg = getQuestionColor();
    HintHint hintHint = new HintHint().setTextBg(bg)
      .setTextFg(JBColor.foreground())
      .setFont(getBoldFont())
      .setAwtTooltip(true);

    return createLabel(text, icon, bg, hintHint);
  }

  @NotNull
  public static SimpleColoredComponent createInformationComponent() {
    SimpleColoredComponent component = new SimpleColoredComponent();
    component.setBackground(getInformationColor());
    component.setForeground(JBColor.foreground());
    component.setFont(getBoldFont());
    return component;
  }

  @NotNull
  public static JComponent createInformationLabel(@NotNull SimpleColoredText text, @Nullable Icon icon) {
    SimpleColoredComponent component = createInformationComponent();
    component.setIcon(icon);
    text.appendToComponent(component);
    return new HintLabel(component);
  }

  public static JComponent createErrorLabel(@NotNull String text,
                                            @Nullable HyperlinkListener hyperlinkListener,
                                            @Nullable MouseListener mouseListener,
                                            @Nullable Ref<Consumer<String>> updatedTextConsumer) {
    Color bg = getErrorColor();
    HintHint hintHint = new HintHint().setTextBg(bg)
                                      .setTextFg(JBColor.foreground())
                                      .setFont(getBoldFont())
                                      .setAwtTooltip(true);

    HintLabel label = createLabel(text, null, bg, hintHint);
    configureLabel(label, hyperlinkListener, mouseListener, updatedTextConsumer);
    return label;
  }

  @NotNull
  public static JComponent createErrorLabel(@NotNull String text) {
    return createErrorLabel(text, null, null, null);
  }

  @NotNull
  private static HintLabel createLabel(String text, @Nullable Icon icon, @NotNull Color color, @NotNull HintHint hintHint) {
    HintLabel label = new HintLabel();
    label.setText(text, hintHint);
    label.setIcon(icon);

    if (!hintHint.isAwtTooltip()) {
      label.setBorder(createHintBorder());
      label.setForeground(JBColor.foreground());
      label.setFont(getBoldFont());
      label.setBackground(color);
      label.setOpaque(true);
    }
    return label;
  }

  private static Font getBoldFont() {
    return UIUtil.getLabelFont().deriveFont(Font.BOLD);
  }

  public static JLabel createAdComponent(final String bottomText, final Border border, @JdkConstants.HorizontalAlignment int alignment) {
    JLabel label = new JLabel();
    label.setText(bottomText);
    label.setHorizontalAlignment(alignment);
    label.setFont(label.getFont().deriveFont((float)(label.getFont().getSize() - 2)));
    if (bottomText != null) {
      label.setBorder(border);
    }
    return label;
  }
  
  @NotNull
  public static String prepareHintText(@NotNull String text, @NotNull HintHint hintHint) {
    return prepareHintText(new Html(text), hintHint);
  }
  
  public static String prepareHintText(@NotNull Html text, @NotNull HintHint hintHint) {
    String htmlBody = UIUtil.getHtmlBody(text);
    return String.format(
      "<html><head>%s</head><body>%s</body></html>",
      UIUtil.getCssFontDeclaration(hintHint.getTextFont(), hintHint.getTextForeground(), hintHint.getLinkForeground(), hintHint.getUlImg()),
      htmlBody
    );
  }

  private static void configureLabel(@NotNull HintLabel label, @Nullable HyperlinkListener hyperlinkListener,
                                     @Nullable MouseListener mouseListener,
                                     @Nullable Ref<Consumer<String>> updatedTextConsumer) {
    if (hyperlinkListener != null) {
      label.myPane.addHyperlinkListener(hyperlinkListener);
    }
    if (mouseListener != null) {
      label.myPane.addMouseListener(mouseListener);
    }
    if (updatedTextConsumer != null) {
      updatedTextConsumer.set(s -> {
        label.myPane.setText(s);

        // Force preferred size recalculation.
        label.setPreferredSize(null);
        label.myPane.setPreferredSize(null);
      });
    }
  }

  private static class HintLabel extends JPanel {
    private JEditorPane myPane;
    private SimpleColoredComponent myColored;
    private JLabel myIcon;

    private HintLabel() {
      setLayout(new BorderLayout());
    }

    private HintLabel(@NotNull SimpleColoredComponent component) {
      this();
      setText(component);
    }

    @Override
    public boolean requestFocusInWindow() {
      // Forward the focus to the tooltip contents so that screen readers announce
      // the tooltip contents right away.
      if (myPane != null) {
        return myPane.requestFocusInWindow();
      } else if (myColored != null) {
        return myColored.requestFocusInWindow();
      } else if (myIcon != null) {
        return myIcon.requestFocusInWindow();
      }
      return super.requestFocusInWindow();
    }

    public void setText(@NotNull SimpleColoredComponent colored) {
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

      myIcon = new JLabel(icon, SwingConstants.CENTER);
      myIcon.setVerticalAlignment(SwingConstants.TOP);

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
