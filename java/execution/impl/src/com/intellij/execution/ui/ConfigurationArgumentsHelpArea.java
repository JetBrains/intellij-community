/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.PopupHandler;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConfigurationArgumentsHelpArea extends JPanel {
  private JTextArea myHelpArea;
  private JPanel myPanel;
  private JLabel myLabel;
  private JPanel myToolbarPanel;

  public ConfigurationArgumentsHelpArea() {
    super(new BorderLayout());
    add(myPanel);
    setBorder(JBUI.Borders.emptyTop(10));

    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyCopyAction());
    myHelpArea.addMouseListener(
      new PopupHandler(){
        @Override
        public void invokePopup(final Component comp,final int x,final int y){
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group).getComponent().show(comp, x, y);
        }
      }
    );

    FixedSizeButton copyButton = new FixedSizeButton(22);
    copyButton.setIcon(PlatformIcons.COPY_ICON);
    copyButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final StringSelection contents = new StringSelection(myHelpArea.getText().trim());
        CopyPasteManager.getInstance().setContents(contents);
      }
    });
    myToolbarPanel.add(copyButton, BorderLayout.NORTH);
    myToolbarPanel.setVisible(false);
  }

  public void setToolbarVisible() {
    myToolbarPanel.setVisible(true);
  }

  public void updateText(final String text) {
    myHelpArea.setText(text);
  }

  public void setLabelText(final String text) {
    myLabel.setText(text);
  }

  public String getLabelText() {
    return myLabel.getText();
  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(ExecutionBundle.message("run.configuration.arguments.help.panel.copy.action.name"));
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final StringSelection contents = new StringSelection(myHelpArea.getText().trim());
      CopyPasteManager.getInstance().setContents(contents);
    }
  }


}
