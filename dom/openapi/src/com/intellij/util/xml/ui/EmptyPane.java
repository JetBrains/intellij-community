package com.intellij.util.xml.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/** 
 * @author cdr
 */

public class EmptyPane {
  private JPanel myPanel;
  private JLabel myLabel;

  public EmptyPane(String text) {
    final Color color = UIUtil.getSeparatorShadow();
    myLabel.setForeground(color);
    myLabel.setText(text);
    myPanel.setBackground(new JTree().getBackground());
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setText(String text) {
    myLabel.setText(text);
  }

  public static void addToPanel(JPanel panel, String text) {
    final EmptyPane emptyPane = new EmptyPane(text);
    panel.setLayout(new BorderLayout());
    panel.add(emptyPane.getComponent(), BorderLayout.CENTER);
  }

}
