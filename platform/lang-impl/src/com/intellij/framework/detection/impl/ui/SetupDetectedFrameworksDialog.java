/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.impl.FrameworkDetectionContextImpl;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class SetupDetectedFrameworksDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private final DetectedFrameworksTree myTree;
  private JPanel myTreePanel;
  private JPanel myOptionsPanel;
  private Splitter mySplitter;
  private JComboBox myGroupByComboBox;
  private JLabel myDescriptionLabel;

  public SetupDetectedFrameworksDialog(Project project, List<DetectedFrameworkDescription> descriptions) {
    super(project, true);
    setTitle("Setup Frameworks");
    final FrameworkDetectionContextImpl context = new FrameworkDetectionContextImpl(project);
    myTree = new DetectedFrameworksTree(descriptions, context, GroupByOption.TYPE) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        updateOptionsPanel();
      }
    };
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myGroupByComboBox.setModel(new EnumComboBoxModel<GroupByOption>(GroupByOption.class));
    myGroupByComboBox.setRenderer(new GroupByListCellRenderer());
    myGroupByComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTree.changeGroupBy((GroupByOption)myGroupByComboBox.getSelectedItem());
      }
    });
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateOptionsPanel();
      }
    });
    init();
    updateOptionsPanel();
  }

  private void updateOptionsPanel() {
    final DetectedFrameworkTreeNodeBase[] nodes = myTree.getSelectedNodes(DetectedFrameworkTreeNodeBase.class, null);
    if (nodes.length == 1) {
      String description = nodes[0].getActionDescription();
      if (description != null) {
        myDescriptionLabel.setText(UIUtil.toHtml(description));
        return;
      }
    }
    myDescriptionLabel.setText("");
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public List<DetectedFrameworkDescription> getSelectedFrameworks() {
    return Arrays.asList(myTree.getCheckedNodes(DetectedFrameworkDescription.class, null));
  }

  public static enum GroupByOption { TYPE, DIRECTORY }

  private class GroupByListCellRenderer extends ListCellRendererWrapper<GroupByOption> {
    public GroupByListCellRenderer() {
      super(SetupDetectedFrameworksDialog.this.myGroupByComboBox);
    }

    @Override
    public void customize(JList list,
                          GroupByOption value,
                          int index,
                          boolean selected,
                          boolean hasFocus) {
      if (value != null) {
        setText(value.name().toLowerCase());
      }
    }
  }
}
