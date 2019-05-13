// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class DummyUIApp {

  private static final String appTitle = "Dummy UI App";

  private static final FocusListener frameFocusListener = new FocusListener() {

    @Override
    public void focusGained(FocusEvent e) {
      System.out.println(appTitle + ": focus lost");
    }

    @Override
    public void focusLost(FocusEvent e) {
      System.out.println(appTitle + ": focus gained");
    }
  };

  public static void main(String[] args) {
    JFrame frame = new JFrame(appTitle);
    frame.add(new JPanel());
    frame.setSize(320, 240);
    frame.pack();
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.addFocusListener(frameFocusListener);
    frame.setVisible(true);
  }

}
