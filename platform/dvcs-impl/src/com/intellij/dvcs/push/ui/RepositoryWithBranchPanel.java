/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.dvcs.push.RepositoryNodeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class RepositoryWithBranchPanel<T extends PushTarget> extends NonOpaquePanel {

  private final JBCheckBox myRepositoryCheckbox;
  private final PushTargetPanel<T> myDestPushTargetPanelComponent;
  private final JBLabel myLocalBranch;
  private final JLabel myArrowLabel;
  private final JLabel myRepositoryLabel;
  private final ColoredTreeCellRenderer myTextRenderer;
  @NotNull private final List<RepositoryNodeListener<T>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public RepositoryWithBranchPanel(@NotNull final Project project, @NotNull String repoName,
                                   @NotNull String sourceName, @NotNull PushTargetPanel<T> destPushTargetPanelComponent) {
    super();
    setLayout(new BorderLayout());
    myRepositoryCheckbox = new JBCheckBox();
    myRepositoryCheckbox.setFocusable(false);
    myRepositoryCheckbox.setOpaque(false);
    myRepositoryCheckbox.setBorder(null);
    myRepositoryCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        fireOnSelectionChange(myRepositoryCheckbox.isSelected());
      }
    });
    myRepositoryLabel = new JLabel(repoName);
    myLocalBranch = new JBLabel(sourceName);
    myArrowLabel = new JLabel(" " + UIUtil.rightArrow() + " ");
    myDestPushTargetPanelComponent = destPushTargetPanelComponent;
    myTextRenderer = new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {

      }
    };
    myTextRenderer.setOpaque(false);
    layoutComponents();

    setInputVerifier(new InputVerifier() {
      @Override
      public boolean verify(JComponent input) {
        ValidationInfo error = myDestPushTargetPanelComponent.verify();
        if (error != null) {
          //noinspection ConstantConditions
          PopupUtil.showBalloonForComponent(error.component, error.message, MessageType.WARNING, false, project);
        }
        return error == null;
      }
    });

    JCheckBox emptyBorderCheckBox = new JCheckBox();
    emptyBorderCheckBox.setBorder(null);
  }

  private void layoutComponents() {
    add(myRepositoryCheckbox, BorderLayout.WEST);
    JPanel panel = new NonOpaquePanel(new BorderLayout());
    panel.add(myTextRenderer, BorderLayout.WEST);
    panel.add(myDestPushTargetPanelComponent, BorderLayout.CENTER);
    add(panel, BorderLayout.CENTER);
  }

  @NotNull
  public String getRepositoryName() {
    return myRepositoryLabel.getText();
  }

  public String getSourceName() {
    return myLocalBranch.getText();
  }

  public String getArrow() {
    return myArrowLabel.getText();
  }

  @NotNull
  public Component getTreeCellEditorComponent(JTree tree,
                                              Object value,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row,
                                              boolean hasFocus) {
    Rectangle bounds = tree.getPathBounds(tree.getPathForRow(row));
    invalidate();
    myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    if (!(value instanceof SingleRepositoryNode)) {
      RepositoryNode node = (RepositoryNode)value;
      myRepositoryCheckbox.setSelected(node.isChecked());
      myRepositoryCheckbox.setVisible(true);
      myTextRenderer.append(getRepositoryName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      myTextRenderer.appendTextPadding(120);
    }
    else {
      myRepositoryCheckbox.setVisible(false);
      myTextRenderer.append(" ");
    }
    myTextRenderer.append(getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myTextRenderer.append(getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    if (bounds != null) {
      setPreferredSize(new Dimension(tree.getVisibleRect().width - bounds.x, bounds.height));
    }
    if (myTextRenderer.getTree().hasFocus()) {
      //delegate focus from tree to editable component if needed
      myDestPushTargetPanelComponent.requestFocus(true);
    }
    revalidate();
    return this;
  }

  public void addRepoNodeListener(@NotNull RepositoryNodeListener<T> listener) {
    myListeners.add(listener);
    myDestPushTargetPanelComponent.addTargetEditorListener(new PushTargetEditorListener() {

      public void onTargetInEditModeChanged(@NotNull String value) {
        for (RepositoryNodeListener listener : myListeners) {
          listener.onTargetInEditMode(value);
        }
      }
    });
  }

  public void fireOnChange() {
    myDestPushTargetPanelComponent.fireOnChange();
    T target = myDestPushTargetPanelComponent.getValue();
    if (target == null) return;
    for (RepositoryNodeListener<T> listener : myListeners) {
      listener.onTargetChanged(target);
    }
  }

  public void fireOnSelectionChange(boolean isSelected) {
    for (RepositoryNodeListener listener : myListeners) {
      listener.onSelectionChanged(isSelected);
    }
  }

  public void fireOnCancel() {
    myDestPushTargetPanelComponent.fireOnCancel();
  }

  public PushTargetPanel getTargetPanel() {
    return myDestPushTargetPanelComponent;
  }

  public boolean isEditable() {
    return myDestPushTargetPanelComponent.getValue() != null;
  }
}


