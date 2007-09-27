/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.MnemonicHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
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
  private JTextArea errorTextArea;
  private JLabel errorLabel;
  private boolean cancelPressed = false;

  public IOExceptionDialog(IOException e, String title, String errorText)  {
    super (JOptionPane.getRootFrame(), title, true);

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

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter writer = new PrintWriter (baos);
    e.printStackTrace(writer);
    writer.flush();
    errorTextArea.setText(baos.toString());
    errorTextArea.setCaretPosition(0);
    errorLabel.setText(errorText);

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

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    Dimension parentSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension ownSize = getPreferredSize();

    setLocation((parentSize.width - ownSize.width) / 2, (parentSize.height - ownSize.height) / 2);

    pack();
  }

  /**
   * Show
   * @return <code>true</code> if "Try Again" button pressed
   * @return <code>false</code> if "Cancel" button pressed
   */
  public static boolean showErrorDialog (IOException e, String title, String text) {
    final IOExceptionDialog dlg = new IOExceptionDialog(e, title, text);
    try {
      final Runnable doRun = new Runnable() {
        public void run() {
          dlg.setVisible(true);
        }
      };
      if (ApplicationManager.getApplication().isDispatchThread()) {
        doRun.run();
      }
      else {
        SwingUtilities.invokeAndWait(doRun);
      }
    }
    catch (InterruptedException e1) {
      e1.printStackTrace();
    }
    catch (InvocationTargetException e1) {
      e1.printStackTrace();
    }

    return ! dlg.cancelPressed;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void main(String[] args) {
    IOExceptionDialog.showErrorDialog(new IOException("test"), "Test", "Something failed");
  }
}
