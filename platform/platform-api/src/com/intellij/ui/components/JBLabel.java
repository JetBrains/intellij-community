// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnchorableComponent;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.JBComponent;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collections;

public class JBLabel extends JLabel implements AnchorableComponent, JBComponent<JBLabel> {
  private UIUtil.ComponentStyle myComponentStyle = UIUtil.ComponentStyle.REGULAR;
  private UIUtil.FontColor myFontColor = UIUtil.FontColor.NORMAL;
  private JComponent myAnchor;
  private JEditorPane myEditorPane;
  private JLabel myIconLabel;
  private boolean myMultiline;
  private boolean myAllowAutoWrapping = false;

  public JBLabel() {
  }

  public JBLabel(@NotNull UIUtil.ComponentStyle componentStyle) {
    setComponentStyle(componentStyle);
  }

  public JBLabel(@Nullable Icon image) {
    super(image);
  }

  public JBLabel(@NotNull @NlsContexts.Label String text) {
    super(text);
  }

  public JBLabel(@NotNull @NlsContexts.Label String text, @NotNull UIUtil.ComponentStyle componentStyle) {
    super(text);
    setComponentStyle(componentStyle);
  }

  public JBLabel(@NotNull @NlsContexts.Label String text, @NotNull UIUtil.ComponentStyle componentStyle, @NotNull UIUtil.FontColor fontColor) {
    super(text);
    setComponentStyle(componentStyle);
    setFontColor(fontColor);
  }

  public JBLabel(@NotNull @NlsContexts.Label String text, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  public JBLabel(@Nullable Icon image, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  public JBLabel(@NotNull @NlsContexts.Label String text, @Nullable Icon icon, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, icon, horizontalAlignment);
  }

  public void setComponentStyle(@NotNull UIUtil.ComponentStyle componentStyle) {
    myComponentStyle = componentStyle;
    UIUtil.applyStyle(componentStyle, this);
  }

  public UIUtil.ComponentStyle getComponentStyle() {
    return myComponentStyle;
  }

  public UIUtil.FontColor getFontColor() {
    return myFontColor;
  }

  public void setFontColor(@NotNull UIUtil.FontColor fontColor) {
    myFontColor = fontColor;
  }

  @Override
  public Color getForeground() {
    if (!isEnabled()) {
      return UIUtil.getLabelDisabledForeground();
    }
    if (myFontColor != null) {
      return UIUtil.getLabelFontColor(myFontColor);
    }
    return super.getForeground();
  }

  @Override
  public void setForeground(Color fg) {
    myFontColor = null;
    super.setForeground(fg);
    if (myEditorPane != null) {
      updateStyle(myEditorPane);
    }
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public Dimension getPreferredSize() {
    if (myAnchor != null && myAnchor != this) return myAnchor.getPreferredSize();
    if (myEditorPane != null) return getLayout().preferredLayoutSize(this);
    return super.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    if (myAnchor != null && myAnchor != this) return myAnchor.getMinimumSize();
    if (myEditorPane != null) return getLayout().minimumLayoutSize(this);
    return super.getMinimumSize();
  }


  @Override
  protected void paintComponent(Graphics g) {
    if (myEditorPane == null) {
      super.paintComponent(g);
    }
  }

  @Override
  public void setText(@NlsContexts.Label String text) {
    super.setText(text);
    if (myEditorPane != null) {
      myEditorPane.setText(getText());
      updateStyle(myEditorPane);
      checkMultiline();
      updateTextAlignment();
    }
  }

  @Override
  public void setIcon(Icon icon) {
    super.setIcon(icon);
    if (myIconLabel != null) {
      myIconLabel.setIcon(icon);
      updateLayout();
      updateTextAlignment();
    }
  }

  public void setIconWithAlignment(Icon icon, int horizontalAlignment, int verticalAlignment) {
    super.setIcon(icon);
    if (myIconLabel != null) {
      myIconLabel.setIcon(icon);
      myIconLabel.setHorizontalAlignment(horizontalAlignment);
      myIconLabel.setVerticalAlignment(verticalAlignment);
      updateLayout();
      updateTextAlignment();
    }
  }


  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    if (myEditorPane != null) {
      myEditorPane.setFocusable(focusable);
    }
  }

  private void checkMultiline() {
    myMultiline = StringUtil.removeHtmlTags(getText()).contains(SystemProperties.getLineSeparator());
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    if (myEditorPane != null) {
      updateStyle(myEditorPane);
      updateTextAlignment();
    }
  }

  @Override
  public void setIconTextGap(int iconTextGap) {
    super.setIconTextGap(iconTextGap);
    if (myEditorPane != null) {
      updateLayout();
    }
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    if (myEditorPane != null) {
      updateTextAlignment();
    }
  }

  @Override
  public void setVerticalTextPosition(int textPosition) {
    super.setVerticalTextPosition(textPosition);
    if (myEditorPane != null) {
      updateTextAlignment();
    }
  }

  protected void updateLayout() {
    setLayout(new BorderLayout(getIcon() == null ? 0 : getIconTextGap(), 0));
    add(myIconLabel, BorderLayout.WEST);
    add(myEditorPane, BorderLayout.CENTER);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myEditorPane != null) {
      //init inner components again (if any) to provide proper colors when LAF is being changed
      setCopyable(false);
      setCopyable(true);
    }

    UISettings.setupComponentAntialiasing(this);
  }

  /**
   * This listener will be used in 'copyable' mode when a link is updated (clicked, entered, etc.).
   */
  @NotNull
  protected HyperlinkListener createHyperlinkListener() {
    return BrowserHyperlinkListener.INSTANCE;
  }

  /**
   * In 'copyable' mode JBLabel has the same appearance but user can select text with mouse and copy it to clipboard with standard shortcut.
   * By default JBLabel is NOT copyable
   * Also 'copyable' label supports web hyperlinks (e.g. opens browser on click)
   *
   * @return 'this' (the same instance)
   */
  public JBLabel setCopyable(boolean copyable) {
    if (copyable ^ myEditorPane != null) {
      if (myEditorPane == null) {
        final JLabel ellipsisLabel = new JBLabel("...");
        myIconLabel = new JLabel(getIcon());
        myEditorPane = new JEditorPane() {
          @Override
          public void paint(Graphics g) {
            Dimension size = getSize();
            boolean paintEllipsis = getPreferredSize().width > size.width && !myMultiline && !myAllowAutoWrapping;

            if (!paintEllipsis) {
              super.paint(g);
            }
            else {
              Dimension ellipsisSize = ellipsisLabel.getPreferredSize();
              int endOffset = size.width - ellipsisSize.width;
              try {
                // do not paint half of the letter
                endOffset = modelToView(viewToModel(new Point(endOffset, getHeight() / 2)) - 1).x;
              }
              catch (BadLocationException ignore) {
              }
              Shape oldClip = g.getClip();
              g.clipRect(0, 0, endOffset, size.height);

              super.paint(g);
              g.setClip(oldClip);

              g.translate(endOffset, 0);
              ellipsisLabel.setSize(ellipsisSize);
              ellipsisLabel.paint(g);
              g.translate(-endOffset, 0);
            }
          }
        };
        myEditorPane.addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            if (myEditorPane == null) return;
            int caretPosition = myEditorPane.getCaretPosition();
            myEditorPane.setSelectionStart(caretPosition);
            myEditorPane.setSelectionEnd(caretPosition);
          }
        });
        myEditorPane.setContentType("text/html");
        myEditorPane.setEditable(false);
        myEditorPane.setBackground(UIUtil.TRANSPARENT_COLOR);
        myEditorPane.setOpaque(false);
        myEditorPane.addHyperlinkListener(createHyperlinkListener());
        ComponentUtil.putClientProperty(myEditorPane, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, Collections.singleton(ellipsisLabel));

        myEditorPane.setEditorKit(UIUtil.getHTMLEditorKit());
        updateStyle(myEditorPane);

        if (myEditorPane.getCaret() instanceof DefaultCaret) {
          ((DefaultCaret)myEditorPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        }
        myEditorPane.setText(getText());
        checkMultiline();
        myEditorPane.setCaretPosition(0);
        updateLayout();
        updateTextAlignment();
      }
      else {
        removeAll();
        myEditorPane = null;
        myIconLabel = null;
      }
    }
    return this;
  }

  private void updateStyle(@NotNull JEditorPane pane) {
    myEditorPane.setFont(getFont());
    myEditorPane.setForeground(getForeground());
    EditorKit kit = pane.getEditorKit();
    if (kit instanceof HTMLEditorKit) {
      StyleSheet css = ((HTMLEditorKit)kit).getStyleSheet();
      css.addRule("body, p {" +
                  "color:#" + ColorUtil.toHex(getForeground()) + ";" +
                  "font-family:" + getFont().getFamily() + ";" +
                  "font-size:" + getFont().getSize() + "pt;" +
                  "white-space:" + (myAllowAutoWrapping ? "normal" : "nowrap") + ";}");
    }
  }

  /**
   * In 'copyable' mode auto-wrapping is disabled by default.
   * (In this case you have to markup your HTML with P or BR tags explicitly)
   */
  public JBLabel setAllowAutoWrapping(boolean allowAutoWrapping) {
    myAllowAutoWrapping = allowAutoWrapping;
    return this;
  }

  private void updateTextAlignment() {
    if (myEditorPane == null) return;

    myEditorPane.setBorder(null); // clear border

    int position = getVerticalTextPosition();
    if (position == TOP) {
      return;
    }

    int preferredHeight = myEditorPane.getPreferredSize().height;
    int availableHeight = getHeight();
    if (availableHeight <= preferredHeight) {
      return;
    }

    // since the 'top' value is in real already-scaled pixels, should use swing's EmptyBorder
    //noinspection UseDPIAwareBorders
    myEditorPane.setBorder(new EmptyBorder(position == CENTER ? (availableHeight - preferredHeight + 1) / 2 :
                                           availableHeight - preferredHeight, 0, 0, 0));
  }

  @Override
  public JBLabel withBorder(Border border) {
    setBorder(border);
    return this;
  }

  @Override
  public JBLabel withFont(JBFont font) {
    setFont(font);
    return this;
  }

  @Override
  public JBLabel andTransparent() {
    setOpaque(false);
    return this;
  }

  @Override
  public JBLabel andOpaque() {
    setOpaque(true);
    return this;
  }
}
