/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.net;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 19, 2003
 * Time: 10:04:14 PM
 * To change this template use Options | File Templates.
 */
public class IOExceptionDialog extends JDialog {
  private JPanel mainPanel;
  private JButton cancelButton;
  private JButton tryAgainButton;
  private JButton setupButton;
  private JTextArea errorLabel;
  private boolean cancelPressed = false;

  public IOExceptionDialog(String title, String errorText)  {
    super(UIUtil.getActiveWindow(), title, DEFAULT_MODALITY_TYPE);

    new MnemonicHelper().register(getContentPane());
    
    getContentPane().add(mainPanel);

    //noinspection HardCodedStringLiteral
    mainPanel.getActionMap().put(
      "close",
      new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          cancelPressed = true;
          dispose();
        }
      }
    );

    //noinspection HardCodedStringLiteral
    mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),
      "close"
    );

    errorLabel.setText(errorText);
    errorLabel.setFont(UIManager.getFont("Label.font"));
    errorLabel.setBackground(UIManager.getColor("Label.background"));
    errorLabel.setForeground(UIManager.getColor("Label.foreground"));

    setupButton.addActionListener(new ActionListener () {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog dlg = new HTTPProxySettingsDialog();
        dlg.show();
      }
    });
    tryAgainButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelPressed = false;
        dispose();
      }
    });
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelPressed = true;
        dispose();
      }
    });

    pack();
    setLocationRelativeTo(getOwner());
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cancelPressed = true;
        dispose();
      }
    });
  }

  /**
   * Show
   * @return <code>true</code> if "Try Again" button pressed
   * @return <code>false</code> if "Cancel" button pressed
   */
  public static boolean showErrorDialog(String title, String text) {
    final IOExceptionDialog dlg = new IOExceptionDialog(title, text);
    try {
      final Runnable doRun = new Runnable() {
        public void run() {
          dlg.setVisible(true);
        }
      };
      GuiUtils.runOrInvokeAndWait(doRun);
    }
    catch (InterruptedException e1) {
      e1.printStackTrace();
    }
    catch (InvocationTargetException e1) {
      e1.printStackTrace();
    }

    return ! dlg.cancelPressed;
  }
}
