/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.webcore.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;

import javax.swing.*;

public class ManageRepoDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private final JBList myList;
  private boolean myEnabled;

  public ManageRepoDialog(Project project, final PackageManagementService controller) {
    super(project, false);
    init();
    setTitle("Manage Repositories");
    final DefaultListModel repoModel = new DefaultListModel();
    for(String repoUrl: controller.getAllRepositories()) {
      repoModel.addElement(repoUrl);
    }
    myList = new JBList();
    myList.setModel(repoModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(event -> {
      final Object selected = myList.getSelectedValue();
      myEnabled = controller.canModifyRepository((String) selected);
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList).disableUpDownActions();
    decorator.setAddActionName("Add repository");
    decorator.setRemoveActionName("Remove repository from list");
    decorator.setEditActionName("Edit repository URL");

    decorator.setAddAction(button -> {
      String url = Messages.showInputDialog("Please input repository URL", "Repository URL", null);
      if (!StringUtil.isEmptyOrSpaces(url) && !repoModel.contains(url)) {
        repoModel.addElement(url);
        controller.addRepository(url);
      }
    });
    decorator.setEditAction(button -> {
      final String oldValue = (String)myList.getSelectedValue();

      String url = Messages.showInputDialog("Please edit repository URL", "Repository URL", null, oldValue, new InputValidator() {
        @Override
        public boolean checkInput(String inputString) {
          return !repoModel.contains(inputString);
        }

        @Override
        public boolean canClose(String inputString) {
          return true;
        }
      });
      if (!StringUtil.isEmptyOrSpaces(url) && !oldValue.equals(url)) {
        repoModel.addElement(url);
        repoModel.removeElement(oldValue);
        controller.removeRepository(oldValue);
        controller.addRepository(url);
      }
    });
    decorator.setRemoveAction(button -> {
      String selected = (String)myList.getSelectedValue();
      controller.removeRepository(selected);
      repoModel.removeElement(selected);
      button.setEnabled(false);
    });
    decorator.setRemoveActionUpdater(e -> myEnabled);
    decorator.setEditActionUpdater(e -> myEnabled);

    final JPanel panel = decorator.createPanel();
    panel.setPreferredSize(JBUI.size(800, 600));
    myMainPanel.add(panel);

  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
