/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInspection.suspiciousNameCombination;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AddEditDeleteListPanel;
import com.intellij.ui.IdeBorderFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.Arrays;

/**
 * @author yole
 */
public class SuspiciousNameCombinationInspection extends SuspiciousNameCombinationInspectionBase {
  public SuspiciousNameCombinationInspection() {
  }

  @Override @Nullable
  public JComponent createOptionsPanel() {
    NameGroupsPanel nameGroupsPanel = new NameGroupsPanel();
    ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(myIgnoredMethods.getClassNames(), myIgnoredMethods.getMethodNamePatterns()),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.method.column.title")));
    JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.class"));
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.add(nameGroupsPanel);
    tablePanel.setBorder(IdeBorderFactory.createTitledBorder("Ignore methods", false));
    panel.add(tablePanel);
    return panel;
  }

  private class NameGroupsPanel extends AddEditDeleteListPanel<String> {

    public NameGroupsPanel() {
      super(InspectionsBundle.message("suspicious.name.combination.options.title"), myNameGroups);
      myListModel.addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
          saveChanges();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
          saveChanges();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
          saveChanges();
        }
      });
    }

    @Override
    protected String findItemToAdd() {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.add.titile"),
                                      Messages.getQuestionIcon(), "", null);
    }

    @Override
    protected String editSelectedItem(String inputValue) {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.edit.title"),
                                      Messages.getQuestionIcon(),
                                      inputValue, null);
    }

    private void saveChanges() {
      clearNameGroups();
      for(int i=0; i<myListModel.getSize(); i++) {
        addNameGroup(myListModel.getElementAt(i));
      }
    }
  }
}
