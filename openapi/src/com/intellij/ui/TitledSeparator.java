package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class TitledSeparator extends JPanel {
  private final JLabel myLabel = new JLabel();

  public TitledSeparator() {
    setLayout(new GridBagLayout());
    add(myLabel, new GridBagConstraints(0,0,1,0,0,0,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,5,0,5), 0,0));
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    add(separator, new GridBagConstraints(1,0,GridBagConstraints.REMAINDER,0,1,0,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,5), 0,0));
  }

  public TitledSeparator(String text) {
    this();
    setText(text);
  }

  public String getText() {
    return myLabel.getText();
  }
  public void setText(String text) {
    myLabel.setText(text);
  }

  public void setTitleFont(Font font) {
    myLabel.setFont(font);
  }
}
