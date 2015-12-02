/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.ui.AnchorableComponent;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.html.parser.ParserDelegator;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;

public class JBLabel extends JLabel implements AnchorableComponent {
  private UIUtil.ComponentStyle myComponentStyle = UIUtil.ComponentStyle.REGULAR;
  private UIUtil.FontColor myFontColor = UIUtil.FontColor.NORMAL;
  private JComponent myAnchor = null;
  private JEditorPane myEditorPane = null;

  public JBLabel() {
    super();
  }

  public JBLabel(@NotNull UIUtil.ComponentStyle componentStyle) {
    super();
    setComponentStyle(componentStyle);
  }

  public JBLabel(@Nullable Icon image) {
    super(image);
  }

  public JBLabel(@NotNull String text) {
    super(text);
  }

  public JBLabel(@NotNull String text, @NotNull UIUtil.ComponentStyle componentStyle) {
    super(text);
    setComponentStyle(componentStyle);
  }

  public JBLabel(@NotNull String text, @NotNull UIUtil.ComponentStyle componentStyle, @NotNull UIUtil.FontColor fontColor) {
    super(text);
    setComponentStyle(componentStyle);
    setFontColor(fontColor);
  }

  public JBLabel(@NotNull String text, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  public JBLabel(@Nullable Icon image, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  public JBLabel(@NotNull String text, @Nullable Icon icon, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
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
    return myAnchor == null || myAnchor == this ? super.getPreferredSize() : myAnchor.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return myAnchor == null || myAnchor == this ? super.getMinimumSize() : myAnchor.getMinimumSize();
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (myEditorPane == null) {
      super.paintComponent(g);
    }
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    if (myEditorPane != null) {
      myEditorPane.setText(getText());
      updateStyle(myEditorPane);
    }
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    if (myEditorPane != null) {
      updateStyle(myEditorPane);
    }
  }


  public JBLabel setCopyable(boolean copyable) {
    if (copyable ^ myEditorPane != null) {
      if (myEditorPane == null) {
        setLayout(new GridLayout(1, 1));
        final JLabel ellipsisLabel = new JBLabel("...");
        myEditorPane = new JEditorPane() {
          @Override
          public void paint(Graphics g) {
            Dimension size = getSize();
            MyHtml2Text parser = new MyHtml2Text();
            String plain;
            try {
              plain = parser.parse(getText());
            }
            catch (IOException e) {
              plain = getText();
            }
            boolean paintEllipsis = getPreferredSize().width > size.width && plain != null && !plain.contains("\n");

            if (!paintEllipsis) {
              super.paint(g);
            }
            else {
              Dimension ellipsisSize = ellipsisLabel.getPreferredSize();
              int endOffset = size.width - ellipsisSize.width;
              try {
                // do not paint half of the letter
                endOffset = modelToView(viewToModel(new Point(endOffset, 0)) - 1).x;
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
        myEditorPane.setBorder(new Border() {
          @Override
          public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
          }

          @Override
          public Insets getBorderInsets(Component c) {
            Icon icon = getIcon();
            int leftGap = icon != null ? icon.getIconWidth() + getIconTextGap() : 0;
            return new Insets(0, leftGap, 0, 0);
          }

          @Override
          public boolean isBorderOpaque() {
            return false;
          }
        });
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
        myEditorPane.setBorder(null);
        myEditorPane.setOpaque(false);
        myEditorPane.setText(getText());
        myEditorPane.setCaretPosition(0);
        UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, Collections.singleton(ellipsisLabel));
        updateStyle(myEditorPane);
        add(myEditorPane);
      } else {
        remove(myEditorPane);
        myEditorPane = null;
      }
    }
    return this;
  }

  private void updateStyle(@NotNull  JEditorPane pane) {
    EditorKit kit = pane.getEditorKit();
    if (kit instanceof HTMLEditorKit) {
      StyleSheet css = ((HTMLEditorKit)kit).getStyleSheet();
      css.addRule("body, p {color:#" + ColorUtil.toHex(getForeground()) + ";font-family:" + getFont().getFamily() + "; font-size:" + getFont().getSize() + "pt;}");
    }
  }

  private static class MyHtml2Text extends HTMLEditorKit.ParserCallback {
    StringBuilder s;

    @Nullable
    public String parse(@Nullable String input) throws IOException {
      if (input == null) return null;
      s = new StringBuilder();
      ParserDelegator delegator = new ParserDelegator();
      delegator.parse(new StringReader(input), this, Boolean.TRUE);
      return s.toString();
    }

    public void handleText(char[] text, int pos) {
      if (s.length() > 0) {
        s.append("\n");
      }
      s.append(text);
    }
  }
}
