/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.panels;

import javax.swing.*;
import java.awt.*;

/**
 * @author kir
 *
 * A label with possible error text is placed under validated component.
 */
public abstract class ValidatingComponent<T extends JComponent> extends NonOpaquePanel {
  private static final Font ERROR_FONT = UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 10f);

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
    label.setForeground(Color.red);
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
