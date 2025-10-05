// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
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

public final class HintUtil {
  /** @deprecated use getInformationColor() */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static final Color INFORMATION_COLOR = new JBColor(0xF7F7F7, 0x4B4D4D);
  /** @deprecated use HINT_BORDER_COLOR_KEY */
  @Deprecated(forRemoval = true)
  public static final Color INFORMATION_BORDER_COLOR = JBColor.namedColor("InformationHint.borderColor", new JBColor(0xE0E0E0, 0x5C5E61));

  public static final ColorKey INFORMATION_COLOR_KEY = ColorKey.createColorKey("INFORMATION_HINT", INFORMATION_COLOR);
  public static final ColorKey QUESTION_COLOR_KEY = ColorKey.createColorKey("QUESTION_HINT", new JBColor(0xb5d0fb, 0x376c89));
  public static final ColorKey WARNING_COLOR_KEY = ColorKey.createColorKey("WARNING_HINT", new JBColor(0xfff8dc, 0x665014));
  public static final ColorKey ERROR_COLOR_KEY = ColorKey.createColorKey("ERROR_HINT", new JBColor(0xffdcdc, 0x781732));
  /**
   * Border color for tooltips with {@link #INFORMATION_COLOR_KEY}, {@link #QUESTION_COLOR_KEY}, {@link #WARNING_COLOR_KEY} and {@link #ERROR_COLOR_KEY}
   */
  public static final ColorKey HINT_BORDER_COLOR_KEY = ColorKey.createColorKey("HINT_BORDER", new JBColor(0xC9CCD6, 0x5A5D63));

  public static final Color QUESTION_UNDERSCORE_COLOR = JBColor.foreground();

  public static final ColorKey RECENT_LOCATIONS_SELECTION_KEY = ColorKey.createColorKey("RECENT_LOCATIONS_SELECTION", new JBColor(0xE9EEF5, 0x383838));
  public static final ColorKey PROMOTION_PANE_KEY = ColorKey.createColorKey("PROMOTION_PANE", new JBColor(0xE6EDF7, 0x233953));

  private HintUtil() { }

  public static @NotNull Color getInformationColor() {
    if (ExperimentalUI.isNewUI()) {
      return HintHint.Status.Info.background;
    }
    return notNull(getGlobalOrDefaultColor(INFORMATION_COLOR_KEY), INFORMATION_COLOR_KEY.getDefaultColor());
  }

  public static @NotNull Color getQuestionColor() {
    return notNull(getGlobalOrDefaultColor(QUESTION_COLOR_KEY), QUESTION_COLOR_KEY.getDefaultColor());
  }

  public static @NotNull Color getWarningColor() {
    if (ExperimentalUI.isNewUI()) {
      return HintHint.Status.Warning.background;
    }
    return notNull(getGlobalOrDefaultColor(WARNING_COLOR_KEY), WARNING_COLOR_KEY.getDefaultColor());
  }

  public static @NotNull Color getErrorColor() {
    if (ExperimentalUI.isNewUI()) {
      return HintHint.Status.Error.background;
    }
    return notNull(getGlobalOrDefaultColor(ERROR_COLOR_KEY), ERROR_COLOR_KEY.getDefaultColor());
  }

  public static @NotNull Color getHintBorderColor() {
    return notNull(getGlobalOrDefaultColor(HINT_BORDER_COLOR_KEY), HINT_BORDER_COLOR_KEY.getDefaultColor());
  }

  public static @NotNull Color getRecentLocationsSelectionColor(EditorColorsScheme colorsScheme) {
    return notNull(colorsScheme.getColor(RECENT_LOCATIONS_SELECTION_KEY), RECENT_LOCATIONS_SELECTION_KEY.getDefaultColor());
  }

  public static JComponent createInformationLabel(@NotNull @HintText String text) {
    return createInformationLabel(text, null, null, null);
  }

  public static JComponent createInformationLabel(@NotNull @HintText String text,
                                                  @Nullable HyperlinkListener hyperlinkListener,
                                                  @Nullable MouseListener mouseListener,
                                                  @Nullable Ref<? super Consumer<@Nls String>> updatedTextConsumer) {
    HintHint hintHint = getInformationHint();
    HintLabel label = createLabel(text, null, hintHint.getTextBackground(), hintHint);
    configureLabel(label, hyperlinkListener, mouseListener, updatedTextConsumer);
    return label;
  }

  public static @NotNull HintHint getInformationHint() {
    //noinspection UseJBColor
    return new HintHint()
      .setBorderColor(getHintBorderColor())
      .setTextBg(getInformationColor())
      .setTextFg(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : Color.black)
      .setFont(getBoldFont())
      .setAwtTooltip(true)
      .setStatus(HintHint.Status.Info);
  }

  public static JComponent createWarningLabel(@NotNull @HintText String text,
                                              @Nullable HyperlinkListener hyperlinkListener,
                                              @Nullable MouseListener mouseListener) {
    Color bg = getWarningColor();
    HintHint hintHint = getWarningHint();
    HintLabel label = createLabel(text, null, bg, hintHint);
    configureLabel(label, hyperlinkListener, mouseListener, null);
    return label;
  }

  public static @NotNull HintHint getWarningHint() {
    HintHint hintHint = new HintHint()
      .setBorderColor(getHintBorderColor())
      .setTextBg(getWarningColor())
      .setTextFg(JBColor.foreground())
      .setFont(getBoldFont())
      .setAwtTooltip(true)
      .setStatus(HintHint.Status.Warning);
    return hintHint;
  }

  public static @NotNull JComponent createWarningLabel(@NotNull @HintText String text) {
    return createWarningLabel(text, null, null);
  }

  public static @NotNull HintHint getSuccessHint() {
    return new HintHint().setAwtTooltip(true).applyStatus(HintHint.Status.Success);
  }

  public static JComponent createSuccessLabel(@NotNull @HintText String text, @Nullable HyperlinkListener hyperlinkListener) {
    HintHint hintHint = getSuccessHint();
    HintLabel label = createLabel(text, null, hintHint.getTextBackground(), hintHint);
    configureLabel(label, hyperlinkListener, null, null);
    return label;
  }

  public static @NotNull JComponent createSuccessLabel(@NotNull @HintText String text) {
    return createSuccessLabel(text, null);
  }

  public static CompoundBorder createHintBorder() {
    return BorderFactory.createCompoundBorder(
      JBUI.Borders.customLine(getHintBorderColor(), 1),
      JBUI.Borders.empty(2)
    );
  }

  public static @NotNull JComponent createInformationLabel(SimpleColoredText text) {
    return createInformationLabel(text, null);
  }

  public static JComponent createQuestionLabel(@HintText String text) {
    Icon icon = AllIcons.General.ContextHelp;
    return createQuestionLabel(text, icon);
  }

  public static JComponent createQuestionLabel(@HintText String text, Icon icon) {
    Color bg = getQuestionColor();
    HintHint hintHint = new HintHint().setTextBg(bg)
      .setBorderColor(getHintBorderColor())
      .setTextFg(JBColor.foreground())
      .setFont(getBoldFont())
      .setAwtTooltip(true)
      .setStatus(HintHint.Status.Info);
    return createLabel(text, ExperimentalUI.isNewUI() ? null : icon, bg, hintHint);
  }

  public static @Nullable String getHintLabel(JComponent hintComponent) {
    return hintComponent instanceof HintLabel ? ((HintLabel)hintComponent).getText() : null;
  }

  public static @Nullable Icon getHintIcon(JComponent hintComponent) {
    return hintComponent instanceof HintLabel ? ((HintLabel)hintComponent).getIcon() : null;
  }

  public static @NotNull SimpleColoredComponent createInformationComponent() {
    return fillInformationComponent(new SimpleColoredComponent());
  }

  @ApiStatus.Internal
  public static @NotNull SimpleColoredComponent fillInformationComponent(SimpleColoredComponent component) {
    component.setTransparentIconBackground(true);
    return installInformationProperties(component);
  }

  public static <T extends Component> @NotNull T installInformationProperties(T component) {
    component.setBackground(getInformationColor());
    component.setForeground(JBColor.foreground());
    component.setFont(getBoldFont());
    return component;
  }

  public static @NotNull JComponent createInformationLabel(@NotNull SimpleColoredText text, @Nullable Icon icon) {
    SimpleColoredComponent component = createInformationComponent();
    component.setIcon(icon);
    text.appendToComponent(component);
    return new HintLabel(component);
  }

  public static JComponent createErrorLabel(@NotNull @HintText String text,
                                            @Nullable HyperlinkListener hyperlinkListener,
                                            @Nullable MouseListener mouseListener) {
    Color bg = getErrorColor();
    HintHint hintHint = new HintHint()
      .setBorderColor(getHintBorderColor())
      .setTextBg(bg)
      .setTextFg(JBColor.foreground())
      .setFont(getBoldFont())
      .setAwtTooltip(true)
      .setStatus(HintHint.Status.Error);
    HintLabel label = createLabel(text, null, bg, hintHint);
    configureLabel(label, hyperlinkListener, mouseListener, null);
    return label;
  }

  public static @NotNull JComponent createErrorLabel(@NotNull @HintText String text) {
    return createErrorLabel(text, null, null);
  }

  @ApiStatus.Internal
  public static @NotNull HintLabel createLabel(@HintText String text, @Nullable Icon icon, @NotNull Color color, @NotNull HintHint hintHint) {
    HintLabel label = new HintLabel();
    label.setText(text, hintHint);
    label.setIcon(icon == null ? hintHint.getStatusIcon() : icon);
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
    return StartupUiUtil.getLabelFont().deriveFont(Font.BOLD);
  }

  public static @NotNull JLabel createAdComponent(@NlsContexts.PopupAdvertisement String bottomText,
                                                  Border border,
                                                  @JdkConstants.HorizontalAlignment int alignment) {
    JLabel label = new JLabel();
    label.setText(bottomText);
    label.setHorizontalAlignment(alignment);
    label.setForeground(JBUI.CurrentTheme.Advertiser.foreground());
    label.setBackground(JBUI.CurrentTheme.Advertiser.background());
    label.setOpaque(true);
    label.setFont(RelativeFont.NORMAL.scale(JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get(), JBUIScale.scale(11f)).derive(StartupUiUtil.getLabelFont()));
    if (bottomText != null) {
      label.setBorder(border);
    }
    return label;
  }

  public static @NotNull @Nls String prepareHintText(@NotNull @HintText String text, @NotNull HintHint hintHint) {
    return prepareHintText(new Html(text), hintHint);
  }

  public static @NotNull @Nls String prepareHintText(@NotNull Html text, @NotNull HintHint hintHint) {
    String htmlBody = UIUtil.getHtmlBody(text);
    String style =
      UIUtil.getCssFontDeclaration(hintHint.getTextFont(), hintHint.getTextForeground(), hintHint.getLinkForeground(), hintHint.getUlImg());
    return HtmlChunk.html().children(
      HtmlChunk.head().addRaw(style),
      HtmlChunk.body().addRaw(htmlBody)
    ).toString();
  }

  private static void configureLabel(@NotNull HintLabel label, @Nullable HyperlinkListener hyperlinkListener,
                                     @Nullable MouseListener mouseListener,
                                     @Nullable Ref<? super Consumer<@Nls String>> updatedTextConsumer) {
    if (hyperlinkListener != null) {
      label.myPane.addHyperlinkListener(hyperlinkListener);
    }
    if (mouseListener != null) {
      label.myPane.addMouseListener(mouseListener);
    }
    if (updatedTextConsumer != null) {
      Consumer<@Nls String> consumer = s -> {
        label.myPane.setText(s);

        // Force preferred size recalculation.
        label.setPreferredSize(null);
        label.myPane.setPreferredSize(null);
      };
      updatedTextConsumer.set(consumer);
    }
  }

  @ApiStatus.Internal
  public static final class HintLabel extends JPanel {
    private JEditorPane myPane;
    private SimpleColoredComponent myColored;
    private JLabel myIcon;

    private @Nullable HintHint hintHint;

    private HintLabel() {
      setLayout(new BorderLayout(ExperimentalUI.isNewUI() ? 6 : 0, 0));
    }

    private HintLabel(@NotNull SimpleColoredComponent component) {
      this();
      setText(component);
    }

    @ApiStatus.Internal
    public @Nullable JEditorPane getPane() {
      return myPane;
    }

    @Override
    public boolean requestFocusInWindow() {
      // Forward the focus to the tooltip contents so that screen readers announce
      // the tooltip contents right away.
      if (myPane != null) {
        return myPane.requestFocusInWindow();
      }
      if (myColored != null) {
        return myColored.requestFocusInWindow();
      }
      if (myIcon != null) {
        return myIcon.requestFocusInWindow();
      }
      return super.requestFocusInWindow();
    }

    public void setText(@NotNull SimpleColoredComponent colored) {
      clearText();
      hintHint = null;

      myColored = colored;
      add(myColored, BorderLayout.CENTER);

      setOpaque(true);
      setBackground(colored.getBackground());

      revalidate();
      repaint();
    }

    public void setText(@NlsContexts.Tooltip String s, HintHint hintHint) {
      clearText();
      this.hintHint = hintHint;

      if (s != null) {
        myPane = IdeTooltipManager.initPane(s, hintHint, null);
        add(myPane, BorderLayout.CENTER);
      }

      setOpaque(true);
      setBackground(hintHint.getTextBackground());

      revalidate();
      repaint();
    }

    public @Nullable HintHint getHintHint() {
      return hintHint;
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

      if (icon != null) {
        myIcon = new JLabel(icon, SwingConstants.CENTER);
        myIcon.setVerticalAlignment(SwingConstants.TOP);

        add(myIcon, BorderLayout.WEST);
      }

      revalidate();
      repaint();
    }

    @Override
    public String toString() {
      return "Hint: text='" + getText() + "'";
    }

    public String getText() {
      return myPane != null ? myPane.getText() : "";
    }

    public @Nullable Icon getIcon() {
      return myIcon != null ? myIcon.getIcon() : null;
    }
  }
}
