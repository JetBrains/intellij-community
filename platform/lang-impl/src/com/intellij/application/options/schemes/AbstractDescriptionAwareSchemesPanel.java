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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public abstract class AbstractDescriptionAwareSchemesPanel<T extends Scheme> extends AbstractSchemesPanel<T, JPanel> {
  private static final String SHOW_DESCRIPTION_CARD = "show.description.card";
  private static final String EDIT_DESCRIPTION_CARD = "edit.description.card";
  private static final String ERROR_CARD = "error.card";

  private final static KeyStroke ESC_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
  private final static KeyStroke ENTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);

  private DescriptionLabel myDescriptionLabel;
  private JLabel myWarningLabel;
  private JBTextField myDescriptionTextField;
  private CardLayout myLayout;

  @NotNull
  @Override
  protected JPanel createInfoComponent() {
    JPanel panel = new JPanel();
    myLayout = new CardLayout();
    panel.setLayout(myLayout);

    myDescriptionTextField = new JBTextField();
    myDescriptionTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        applyDescription();
      }
    });
    myDescriptionTextField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showDescription();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(getConfigurableFocusComponent(), true);
        });
      }
    }, ESC_KEY_STROKE, JComponent.WHEN_FOCUSED);
    myDescriptionTextField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        applyDescription();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(getConfigurableFocusComponent(), true);
        });
      }
    }, ENTER_KEY_STROKE, JComponent.WHEN_FOCUSED);

    myDescriptionLabel = new DescriptionLabel();
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (clickCount != 2) {
          return false;
        }
        editDescription(myDescriptionLabel.getText());
        return true;
      }
    }.installOn(myDescriptionLabel);

    myWarningLabel = new JLabel();

    panel.add(myDescriptionTextField, EDIT_DESCRIPTION_CARD);
    panel.add(myDescriptionLabel, SHOW_DESCRIPTION_CARD);
    panel.add(myWarningLabel, ERROR_CARD);

    myLayout.show(panel, ERROR_CARD);
    return panel;
  }

  @Override
  public final void showMessage(@Nullable String message, @NotNull MessageType messageType) {
    myWarningLabel.setText(message);
    myWarningLabel.setForeground(messageType.getTitleForeground());
    myLayout.show(myInfoComponent, ERROR_CARD);
  }

  @Override
  public void selectScheme(@Nullable T scheme) {
    super.selectScheme(scheme);
    if (scheme != null) {
      showDescription();
    }
  }

  @Override
  public final void clearMessage() {
    myLayout.show(myInfoComponent, SHOW_DESCRIPTION_CARD);
  }

  public void showDescription() {
    String newDescription = (((DescriptionAwareSchemeActions<T>)getActions()).getDescription(getSelectedScheme()));
    myDescriptionLabel.setAllText(StringUtil.notNullize(newDescription));
    myLayout.show(myInfoComponent, SHOW_DESCRIPTION_CARD);
  }

  public void editDescription(@Nullable String startValue) {
    myLayout.show(myInfoComponent, EDIT_DESCRIPTION_CARD);
    myDescriptionTextField.setText(StringUtil.notNullize(startValue));
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(myDescriptionTextField, true);
    });
  }

  @NotNull
  protected abstract JComponent getConfigurableFocusComponent();

  private void applyDescription() {
    (((DescriptionAwareSchemeActions<T>)getActions())).setDescription(getSelectedScheme(), myDescriptionTextField.getText());
    showDescription();
  }

  private static class DescriptionLabel extends MultiLineLabel {
    private String myAllText;

    public DescriptionLabel() {
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this);
      setForeground(JBColor.GRAY);
      setVerticalAlignment(CENTER);
      setHorizontalAlignment(LEFT);
    }

    public void setAllText(String allText) {
      myAllText = allText;
      revalidate();
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      int width = getSize().width;
      FontMetrics fontMetrics = getFontMetrics(getFont());
      int charWidth = fontMetrics.charWidth('a');
      int firstLineSize = Math.max(0, width / charWidth);
      if (myAllText.length() <= firstLineSize) {
        setText(myAllText);
        setToolTipText(null);
      } else {
        String firstLine = myAllText.substring(0, firstLineSize);
        String remainPart = myAllText.substring(firstLineSize);
        int lastWhitespace = firstLine.lastIndexOf(' ');
        if (lastWhitespace > -1) {
          remainPart = firstLine.substring(lastWhitespace) + remainPart;
          firstLine = firstLine.substring(0, lastWhitespace);
        }
        String visibleText = firstLine + "\n";

        int secondLineSize = Math.max(0, (width - 3 * fontMetrics.charWidth('.')) / charWidth);
        if (remainPart.length() <= secondLineSize) {
          setText(visibleText + remainPart.trim());
          setToolTipText(null);
        } else {
          setText(visibleText + remainPart.trim().substring(0, secondLineSize) + "...");
          setToolTipText("..." + remainPart.substring(secondLineSize));
        }
      }
      super.paintComponent(g);
    }
  }
}