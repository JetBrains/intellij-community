
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
package com.intellij.refactoring.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class InfoDialog extends DialogWrapper{
  private JCheckBox myShowInFutureCheckBox;
  private JTextArea myTextArea;
  private final String myText;
  private boolean isToShowInFuture;

  public InfoDialog(String text, Project project) {
    super(project, false);
    myText = text;
    setButtonsAlignment(SwingUtilities.CENTER);
    setTitle(RefactoringBundle.message("information.title"));
    init();
    setOKButtonText(RefactoringBundle.message("ok.button"));
  }

  @NotNull
  protected Action[] createActions(){
    return new Action[]{getOKAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEtchedBorder());
    panel.setLayout(new BorderLayout());

    JPanel cbPanel = new JPanel(new BorderLayout());
    cbPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    myShowInFutureCheckBox = new JCheckBox();
    myShowInFutureCheckBox.setText(RefactoringBundle.message("do.not.show.this.message.in.the.future"));
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
