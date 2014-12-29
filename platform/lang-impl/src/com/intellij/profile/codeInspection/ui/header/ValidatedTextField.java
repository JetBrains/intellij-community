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

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Dmitry Batkovich
 */
public class ValidatedTextField extends JBTextField {
  private static final String LABEL_CARD = "label";
  private static final String NO_LABEL_CARD = "no_label";

  private final SaveInputComponentValidator myInputValidator;
  private final JPanel myHintPanel;

  private boolean myIgnoreFocus;

  public ValidatedTextField(final SaveInputComponentValidator inputValidator) {
    myInputValidator = inputValidator;
    getDocument().addDocumentListener(new DocumentListener() {
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
        final boolean isValid = myInputValidator.checkValid(getText());
        final Color color = isValid ? UIUtil.getTextAreaForeground() : JBColor.RED;
        if (!color.equals(getForeground())) {
          setForeground(color);
        }
      }
    });

    addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        if (!myIgnoreFocus) {
          checkAndApply();
        }
      }
    });

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          e.consume();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          checkAndApply();
          e.consume();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          myIgnoreFocus = true;
          myInputValidator.cancel();
          e.consume();
        }
      }
    });

    myHintPanel = new JPanel();
    final CardLayout cardLayout = new CardLayout();
    myHintPanel.setLayout(cardLayout);

    JLabel hintLabel = new JLabel("Save: Enter, Cancel: Esc");
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, hintLabel);
    hintLabel.setForeground(UIUtil.getLabelDisabledForeground());
    myHintPanel.add(hintLabel, LABEL_CARD);
    myHintPanel.add(new JPanel(), NO_LABEL_CARD);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        cardLayout.show(myHintPanel, LABEL_CARD);
        myIgnoreFocus = false;
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        cardLayout.show(myHintPanel, NO_LABEL_CARD);
      }
    });

    cardLayout.show(myHintPanel, NO_LABEL_CARD);
  }

  public JPanel getHintLabel() {
    return myHintPanel;
  }

  private void checkAndApply() {
    String text = getText();
    if (text == null) {
      text = "";
    }
    if (myInputValidator.checkValid(text)) {
      myInputValidator.doSave(text);
    }
  }
}
