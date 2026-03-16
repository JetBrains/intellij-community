// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import org.cef.callback.CefStringVisitor;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ApiStatus.Internal
public class  ShowTextDialog extends JDialog implements CefStringVisitor {
  private final JTextArea textArea_ = new JTextArea();

  public ShowTextDialog(Frame owner, String title) {
    super(owner, title, false);
    setLayout(new BorderLayout());
    setSize(800, 600);

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
    JButton doneButton = new JButton("Done");
    doneButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
        dispose();
      }
    });
    controlPanel.add(doneButton);

    add(new JScrollPane(textArea_));
    add(controlPanel, BorderLayout.SOUTH);
  }

  @Override
  public void visit(String string) {
    if (!isVisible()) {
      setVisible(true);
    }
    textArea_.append(string);
  }
}
