package com.intellij.ui;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: beg
 * Date: Oct 11, 2004
 * Time: 10:01:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class ScrollPaneFactory {
  public static JScrollPane2 createScrollPane() {
    return new JScrollPane2();
  }

  public static JScrollPane createScrollPane(JComponent view) {
    return new JScrollPane2(view);
  }
}
