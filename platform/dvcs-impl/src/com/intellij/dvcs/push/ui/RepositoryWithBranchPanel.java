// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class RepositoryWithBranchPanel<T extends PushTarget> extends NonOpaquePanel {

  private final JBCheckBox myRepositoryCheckbox;
  private final PushTargetPanel<T> myDestPushTargetPanelComponent;
  private final @Nls String myRepositoryName;
  private final @Nls String mySourceName;
  private final ColoredTreeCellRenderer myTextRenderer;
  @NotNull private final List<RepositoryNodeListener<T>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public RepositoryWithBranchPanel(@NotNull final Project project, @NotNull @Nls String repoName,
                                   @NotNull @Nls String sourceName, @NotNull PushTargetPanel<T> destPushTargetPanelComponent) {
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
    myRepositoryName = repoName;
    mySourceName = sourceName;
    myDestPushTargetPanelComponent = destPushTargetPanelComponent;
    myTextRenderer = new ColoredTreeCellRenderer() {
      @Override
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

  @Nls
  @NotNull
  public String getRepositoryName() {
    return myRepositoryName;
  }

  @Nls
  public String getSourceName() {
    return mySourceName;
  }

  @Nls
  public String getArrow() {
    return " " + UIUtil.rightArrow() + " ";
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
    if (value instanceof SingleRepositoryNode) {
      myTextRenderer.setIpad(JBUI.insetsLeft(10));
      myRepositoryCheckbox.setVisible(false);
    } else {
      RepositoryNode node = (RepositoryNode)value;
      myRepositoryCheckbox.setSelected(node.isChecked());
      myRepositoryCheckbox.setVisible(true);
      myTextRenderer.setIpad(JBUI.emptyInsets());
      myTextRenderer.append(getRepositoryName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      myTextRenderer.appendTextPadding(120);
    }

    if (myDestPushTargetPanelComponent.showSourceWhenEditing()) {
      if (value instanceof SingleRepositoryNode) {
        myTextRenderer.append(" ");
      }
      myTextRenderer.append(getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myTextRenderer.append(getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
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

      @Override
      public void onTargetInEditModeChanged(@NotNull @Nls String value) {
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


