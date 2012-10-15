/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTest {
  private JBCheckBox myJBCheckBox1;
  private JBCheckBox myJBCheckBox2;
  private JBCheckBox myJBCheckBox3;
  private JBCheckBox myJBCheckBox4;
  private JBCheckBox myJBCheckBox5;
  private JComboBox myComboBox1;
  private JComboBox myComboBox2;
  private JComboBox myComboBox3;
  private JComboBox myComboBox4;
  private JComboBox myComboBox5;
  private JTextField myTextField1;
  private JTextField myThisTextIsDisabledTextField;
  private JPasswordField myPasswordField1;
  private JPanel myRoot;

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(new DarculaLaf());
    }
    catch (UnsupportedLookAndFeelException ignored) {}
    final JFrame frame = new JFrame("Darcula Demo");
    frame.setSize(600, 500);
    frame.setContentPane(new DarculaTest().myRoot);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        frame.setVisible(true);
      }
    });
  }
}
