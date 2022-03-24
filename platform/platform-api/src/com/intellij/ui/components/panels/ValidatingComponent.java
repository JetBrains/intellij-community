// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.panels;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.StartupUiUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author kir
 *
 * A label with possible error text is placed under validated component.
 */
public abstract class ValidatingComponent<T extends JComponent> extends NonOpaquePanel {
  private static final Font ERROR_FONT = StartupUiUtil.getLabelFont().deriveFont(Font.PLAIN, 10f);

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

  public void setErrorText(@NlsContexts.Label String errorText) {
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

  @Override
  public void doLayout() {
    super.doLayout();
    if (myLabel != null) {
      myErrorLabel.setBorder(BorderFactory.createEmptyBorder(0, myLabel.getSize().width, 0, 0));
    }
  }
}
