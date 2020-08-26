// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ScopesOrderDialog extends DialogWrapper {
  private final JList<NamedScope> myOptionsList = new JBList<>();
  private final InspectionProfileImpl myInspectionProfile;
  @NotNull
  private final Project myProject;
  private final JPanel myPanel;
  private final MyModel myModel;

  ScopesOrderDialog(@NotNull final Component parent,
                    @NotNull InspectionProfileImpl inspectionProfile,
                    @NotNull Project project) {
    super(parent, true);
    myInspectionProfile = inspectionProfile;
    myProject = project;
    myModel = new MyModel();
    reloadScopeList();
    myOptionsList.setModel(myModel);
    myOptionsList.setSelectedIndex(0);
    myOptionsList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value instanceof NamedScope) {
          setText(((NamedScope)value).getPresentableName());
        }
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    final JPanel listPanel = ToolbarDecorator.createDecorator(myOptionsList).setMoveDownAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        ListUtil.moveSelectedItemsDown(myOptionsList);
      }
    }).setMoveUpAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        ListUtil.moveSelectedItemsUp(myOptionsList);
      }
    }).addExtraAction(new AnActionButton(CodeInsightBundle.messagePointer("action.AnActionButton.text.edit.scopes"), AllIcons.Actions.Edit) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance().editConfigurable(project, new ScopeChooserConfigurable(project));
        reloadScopeList();
      }
    }).disableRemoveAction().disableAddAction().createPanel();
    final JLabel descr = new JLabel(AnalysisBundle.message("inspections.settings.scopes.order.help.label"));
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, descr);
    myPanel = new JPanel();
    myPanel.setLayout(new BorderLayout());
    myPanel.add(listPanel, BorderLayout.CENTER);
    myPanel.add(descr, BorderLayout.SOUTH);
    init();
    setTitle(AnalysisBundle.message("inspections.settings.scopes.order.title"));
  }

  private void reloadScopeList() {
    myModel.removeAllElements();

    final List<NamedScope> scopes = new ArrayList<>();
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(myProject)) {
      for (final NamedScope scope : holder.getScopes()) {
        if (!(scope instanceof NonProjectFilesScope)) {
          scopes.add(scope);
        }
      }
    }
    scopes.remove(CustomScopesProviderEx.getAllScope());
    scopes.sort(Comparator.comparing(namedScope -> namedScope.getScopeId(), new ScopeOrderComparator(myInspectionProfile)));
    for (NamedScope scope : scopes) {
      myModel.addElement(scope);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    final int size = myOptionsList.getModel().getSize();
    final String[] newScopeOrder = new String[size];
    for (int i = 0; i < size; i++) {
      final NamedScope namedScope = myOptionsList.getModel().getElementAt(i);
      newScopeOrder[i] = namedScope.getScopeId();
    }
    if (!Arrays.equals(newScopeOrder, myInspectionProfile.getScopesOrder())) {
      myInspectionProfile.setScopesOrder(newScopeOrder);
    }
    super.doOKAction();
  }

  private static class MyModel extends DefaultListModel<NamedScope> implements EditableModel {
    @Override
    public void addRow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      NamedScope scope1 = getElementAt(newIndex);
      set(newIndex, getElementAt(oldIndex));
      set(oldIndex, scope1);
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }

    @Override
    public void removeRow(int idx) {
      throw new UnsupportedOperationException();
    }
  }
}
