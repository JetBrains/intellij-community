/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.components.panels;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author kir
 *
 * A label with possible error text is placed under validated component.
 */
public abstract class ValidatingComponent<T extends JComponent> extends NonOpaquePanel {
  private static final Font ERROR_FONT = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 10f);

  private JLabel myErrorLabel;
  private T myMainComponent;
  private JLabel myLabel;

  protected ValidatingComponent() {
    setLayout(new BorderLayout());
  }

  public final void doInitialize() {
    myErrorLabel = createErrorLabel();
    myMainComponent = createMainComponent();

    add(myMainComponent, BorderLayout.CENTER);
    add(myErrorLabel, BorderLayout.SOUTH);
  }

  protected abstract T createMainComponent();

  public void setErrorText(String errorText) {
    if ("".equals(errorText) || errorText == null) {
      errorText = " ";
    }
    myErrorLabel.setText(errorText);
  }

  public JLabel getErrorLabel() {
    return myErrorLabel;
  }

  public T getMainComponent() {
    return myMainComponent;
  }

  public String  getErrorText() {
    final String text = myErrorLabel.getText();
    return " ".equals(text) ? "" : text;
  }

  public void setLabel(JLabel label) {
    myLabel = label;
    myLabel.setLabelFor(myMainComponent);
    add(label, BorderLayout.WEST);
  }

  protected JLabel createErrorLabel() {
    final JLabel label = new JLabel(" ");
    label.setForeground(JBColor.red);
    label.setFont(ERROR_FONT);
    return label;
  }

  public void doLayout() {
    super.doLayout();
    if (myLabel != null) {
      myErrorLabel.setBorder(BorderFactory.createEmptyBorder(0, myLabel.getSize().width, 0, 0));
    }
  }
}
