/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

public class LabeledComponent<Comp extends JComponent> extends JPanel {
  private final JLabel myLabel = new JLabel();
  private Comp myCompoenent;
  private String myLabelConstrains = BorderLayout.NORTH;

  public LabeledComponent() {
    super(new BorderLayout());
    insertLabel();
    updateLabelBorder();
    updateUI();
  }

  private void updateLabelBorder() {
    int left = 0;
    int bottom = 0;
    if (BorderLayout.NORTH.equals(myLabelConstrains)) {
      bottom = 2;
    }
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, left, bottom, 0));
  }

  public void updateUI() {
    super.updateUI();
    if (myLabel != null) updateLabelBorder();
  }

  private void insertLabel() {
    remove(myLabel);
    add(myLabel, myLabelConstrains);
  }

  public void setText(String textWithMnemonic) {
    if (!StringUtil.endsWithChar(textWithMnemonic, ':')) textWithMnemonic += ":";
    TextWithMnemonic withMnemonic = TextWithMnemonic.fromTextWithMnemonic(textWithMnemonic);
    withMnemonic.setToLabel(myLabel);
  }
  public String getText() {
    String text = TextWithMnemonic.fromLabel(myLabel).getTextWithMnemonic();
    if (StringUtil.endsWithChar(text, ':')) text.substring(0, text.length() - 1);
    return text;
  }

  public void setComponentClass(String className) throws ClassNotFoundException, InstantiationException,
                                                         IllegalAccessException {
    Class<Comp> aClass = (Class<Comp>)getClass().getClassLoader().loadClass(className);
    Comp component = aClass.newInstance();
    setComponent(component);
  }

  public void setComponent(Comp component) {
    if (myCompoenent != null) remove(myCompoenent);
    myCompoenent = component;
    add(myCompoenent, BorderLayout.CENTER);
    if (myCompoenent instanceof ComponentWithBrowseButton) {
      myLabel.setLabelFor(((ComponentWithBrowseButton)myCompoenent).getChildComponent());
    } else myLabel.setLabelFor(myCompoenent);
  }

  public String getComponentClass() {
    if (myCompoenent == null) return null;
    return getComponent().getClass().getName();
  }

  public Comp getComponent() {
    return myCompoenent;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (myCompoenent != null) myCompoenent.setEnabled(enabled);
    myLabel.setEnabled(enabled);
  }

  public void setLabelLocation(String borderConstrains) {
    String constrains = findBorderConstrains(borderConstrains);
    if (constrains == null || constrains == myLabelConstrains) return;
    myLabelConstrains = borderConstrains;
    insertLabel();
  }

  public String getLabelLocation() {
    return myLabelConstrains;
  }

  public Insets getLabelInsets() {
    return myLabel.getInsets();
  }

  public void setLabelInsets(Insets insets) {
    if (Comparing.equal(insets, getLabelInsets())) return;
    myLabel.setBorder(IdeBorderFactory.createEmptyBorder(insets));
  }

  private static final String[] LABEL_BORDER_CONSTRAINS = new String[]{BorderLayout.NORTH, BorderLayout.EAST, BorderLayout.SOUTH, BorderLayout.WEST};

  private String findBorderConstrains(String str) {
    for (int i = 0; i < LABEL_BORDER_CONSTRAINS.length; i++) {
      String constrain = LABEL_BORDER_CONSTRAINS[i];
      if (constrain.equals(str)) return constrain;
    }
    return null;
  }

  public String getRawText() {
    return myLabel.getText();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public static class TextWithMnemonic {
    private final String myText;
    private final int myMnemoniIndex;

    public TextWithMnemonic(String text, int mnemoniIndex) {
      myText = text;
      myMnemoniIndex = mnemoniIndex;
    }

    public void setToLabel(JLabel label) {
      label.setText(myText);
      if (myMnemoniIndex != -1) label.setDisplayedMnemonic(myText.charAt(myMnemoniIndex));
      else label.setDisplayedMnemonic(0);
      label.setDisplayedMnemonicIndex(myMnemoniIndex);
    }

    public String getTextWithMnemonic() {
      if (myMnemoniIndex == -1) return myText;
      return myText.substring(0, myMnemoniIndex) + "&" + myText.substring(myMnemoniIndex);
    }

    public static TextWithMnemonic fromTextWithMnemonic(String textWithMnemonic) {
      int mnemonicIndex = textWithMnemonic.indexOf('&');
      if (mnemonicIndex == -1) {
        return new TextWithMnemonic(textWithMnemonic, -1);
      }
      textWithMnemonic = textWithMnemonic.substring(0, mnemonicIndex) + textWithMnemonic.substring(mnemonicIndex + 1);
      return new TextWithMnemonic(textWithMnemonic, mnemonicIndex);
    }

    public static TextWithMnemonic fromLabel(JLabel label) {
      return new TextWithMnemonic(label.getText(), label.getDisplayedMnemonicIndex());
    }
  }
}
