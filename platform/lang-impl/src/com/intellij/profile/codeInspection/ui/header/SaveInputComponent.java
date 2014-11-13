/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.header;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Dmitry Batkovich
 */
public class SaveInputComponent extends JPanel {
  private final JButton myButton;
  private final JTextField myTextField;
  private final SaveInputComponentValidator myInputValidator;

  public SaveInputComponent(final SaveInputComponentValidator inputValidator) {
    myButton = new JButton() {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
      }
    };
    myButton.setText("Save");
    myButton.setDefaultCapable(false);
    myButton.setFocusable(false);
    myButton.putClientProperty("JButton.buttonType", "square");
    //myButton.setUI(new DarculaButtonUI() {
    //  @Override
    //  public void paint(Graphics g, JComponent c) {
    //    final Color color1 = getButtonColor1();
    //    final Color color2 = getButtonColor2();
    //    if (color1 != null && color2 != null) {
    //      ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, color1, 0, c.getHeight(), color2));
    //    }
    //    //g.fillRoundRect(2, 0, c.getWidth() - 3, c.getHeight() - 2, 5, 5);
    //    super.paint(g, c);n
    //  }
    //});
    myButton.setBorder(new DarculaButtonPainter() {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        final Graphics2D g2d = (Graphics2D)g;
        final GraphicsConfig config = new GraphicsConfig(g);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
        ((Graphics2D)g).setPaint(Gray._100.withAlpha(180));
        //g.drawRoundRect(x + 2, y, width - 3, height - 2, 5, 5);
        g.drawRoundRect(x + 2, y, width - 3 - 5, height - 2, 5, 5);
        config.restore();
      }

      @Override
      protected int getOffset() {
        return 5;
      }

      @Override
      public Insets getBorderInsets(Component c) {
        return new Insets(2, 10, 2, 10);
      }
    });

    myTextField = new JBTextField();
    myTextField.setFocusable(true);
    setFocusable(false);
    setLayout(new BorderLayout());
    add(myTextField, BorderLayout.CENTER);
    add(myButton, BorderLayout.EAST);

    myInputValidator = inputValidator;
    myTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        changedUpdate(e);
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        changedUpdate(e);
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        final boolean isValid = myInputValidator.checkValid(myTextField.getText());
        myButton.setEnabled(isValid);
        final Color color = isValid ? UIUtil.getTextAreaForeground() : JBColor.RED;
        if (!color.equals(myTextField.getForeground())) {
          myTextField.setForeground(color);
        }
      }
    });

    myTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        checkAndApply();
      }
    });

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        checkAndApply();
        return true;
      }
    }.installOn(myButton);
  }

  public void requestFocusToTextField() {
    myTextField.requestFocus();
  }

  public SaveInputComponent setText(final @NotNull String textFieldValue) {
    myTextField.setText(textFieldValue);
    return this;
  }

  private void checkAndApply() {
    String text = myTextField.getText();
    if (text == null) {
      text = "";
    }
    if (myInputValidator.checkValid(text)) {
      myInputValidator.doSave(text);
    }
  }
}
