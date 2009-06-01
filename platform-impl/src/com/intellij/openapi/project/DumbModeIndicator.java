/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.wm.impl.IdeRootPaneNorthExtension;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.LightColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author peter
 */
class DumbModeIndicator extends IdeRootPaneNorthExtension {
  private final JLabel myComponent = new JLabel("Index update is in progress...");

  protected DumbModeIndicator(Project project) {
    project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      public void beforeEnteringDumbMode() {
      }

      public void enteredDumbMode() {
        myComponent.setVisible(true);
      }

      public void exitDumbMode() {
        myComponent.setVisible(false);
      }
    });

    myComponent.setBorder(new EmptyBorder(3, 7, 3, 3));
    myComponent.setOpaque(true);
    myComponent.setBackground(LightColors.YELLOW);

    myComponent.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          Messages.showMessageDialog("<html>" +
                                     "IntelliJ IDEA is now indexing your source and library files. These indices are<br>" +
                                     "needed for most of the smart functionality to work properly." +
                                     "<p>" +
                                     "During this process some actions that require these indices won't be available,<br>" +
                                     "although you still can edit your files and work with VCS and file system.<br>" +
                                     "If you need smarter actions like Goto Declaration, Find Usages or refactorings,<br>" +
                                     "please wait until the update is finished. We appreciate your understanding." +
                                     "</html>", "Don't panic!", null);
        }
      });
      myComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  public String getKey() {
    return "dumb mode indicator";
  }

  public void uiSettingsChanged(UISettings settings) {
  }

  public void dispose() {
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void installComponent(Project project, JPanel northPanel) {
    northPanel.add(myComponent, new GridBagConstraints(0, 2, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    myComponent.setVisible(DumbService.getInstance(project).isDumb());
  }

}
