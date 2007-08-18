/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class ConfigurationArgumentsHelpArea extends JPanel {
  private JTextArea myHelpArea;
  private JPanel myPanel;
  private JLabel myLabel;

  public ConfigurationArgumentsHelpArea() {
    super(new BorderLayout());
    myHelpArea.addMouseListener(
      new PopupHandler(){
        public void invokePopup(final Component comp,final int x,final int y){
          createPopupMenu().getComponent().show(comp,x,y);
        }
      }
    );
    add(myPanel);
  }

  private ActionPopupMenu createPopupMenu() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyCopyAction());
    return ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN,group);
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
    }

    public void actionPerformed(final AnActionEvent e) {
      try {
        final StringSelection contents = new StringSelection(myHelpArea.getText().trim());
        final Project project = DataKeys.PROJECT.getData(e.getDataContext());
        if (project == null) {
          final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
          clipboard.setContents(contents, new ClipboardOwner() {
            public void lostOwnership(final Clipboard c, final Transferable contents) {
            }
          });
        } else {
          CopyPasteManager.getInstance().setContents(contents);
        }
      } catch(Exception ex) {
      }
    }
  }


}
