/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.labels;

import javax.swing.*;

/**
 * @author kir
 */
@SuppressWarnings({"ClassWithTooManyConstructors"})
public class BoldLabel extends JLabel {

  public BoldLabel() {
  }

  public BoldLabel(String text) {
    super(toHtml(text));
  }

  public BoldLabel(String text, int horizontalAlignment) {
    super(toHtml(text), horizontalAlignment);
  }

  public BoldLabel(Icon image) {
    super(image);
  }

  public BoldLabel(Icon image, int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  public BoldLabel(String text, Icon icon, int horizontalAlignment) {
    super(toHtml(text), icon, horizontalAlignment);
  }

  public void setText(String text) {
    super.setText(toHtml(text));
  }

  private static String toHtml(String text) {
    if (text.startsWith("<html>")) return text;
    return "<html><b>" + text.replaceAll("\\n", "<br>") + "</b></html>";
  }

  @SuppressWarnings({"MagicNumber"})
  public static void main(String[] args) {
    JFrame frame = new JFrame();
    frame.getContentPane().add(new BoldLabel("bebebe\nlalala\nfufufu"));
    frame.setSize(400, 300);
    frame.setVisible(true);
  }
}
