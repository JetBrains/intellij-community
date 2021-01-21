
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.ui;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class InfoDialog extends DialogWrapper{
  private JCheckBox myShowInFutureCheckBox;
  private JTextArea myTextArea;
  private final @Nls String myText;
  private boolean isToShowInFuture;

  public InfoDialog(@Nls String text, Project project) {
    super(project, false);
    myText = text;
    setTitle(JavaRefactoringBundle.message("information.title"));
    init();
    setOKButtonText(JavaRefactoringBundle.message("ok.button"));
  }

  @Override
  protected Action @NotNull [] createActions(){
    return new Action[]{getOKAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEtchedBorder());
    panel.setLayout(new BorderLayout());

    JPanel cbPanel = new JPanel(new BorderLayout());
    cbPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    myShowInFutureCheckBox = new JCheckBox();
    myShowInFutureCheckBox.setText(JavaRefactoringBundle.message("do.not.show.this.message.in.the.future"));
    panel.add(cbPanel, BorderLayout.SOUTH);
    cbPanel.add(myShowInFutureCheckBox, BorderLayout.WEST);

    JPanel textPanel = new JPanel(new BorderLayout());
    textPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
    panel.add(textPanel, BorderLayout.CENTER);

    myTextArea = new JTextArea(myText);
    textPanel.add(myTextArea, BorderLayout.CENTER);
    myTextArea.setEditable(false);
    myTextArea.setBackground(UIUtil.getPanelBackground());
    Font font = myShowInFutureCheckBox.getFont();
    font = new Font(font.getName(), font.getStyle(), font.getSize() + 1);
    myTextArea.setFont(font);
    myShowInFutureCheckBox.setFont(font);
    isToShowInFuture = true;
    myShowInFutureCheckBox.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          isToShowInFuture = !myShowInFutureCheckBox.isSelected();
        }
      }
    );
    return panel;
  }

  public boolean isToShowInFuture() {
    return isToShowInFuture;
  }
}
