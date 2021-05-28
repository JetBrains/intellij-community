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
import com.intellij.profile.codeInspection.ui.table.ScopesOrderTable;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScopesOrderDialog extends DialogWrapper {
  private final ScopesOrderTable myOptionsTable;
  private final InspectionProfileImpl myInspectionProfile;
  @NotNull
  private final Project myProject;
  private final JPanel myPanel;

  ScopesOrderDialog(@NotNull final Component parent,
                    @NotNull InspectionProfileImpl inspectionProfile,
                    @NotNull Project project) {
    super(parent, true);
    myInspectionProfile = inspectionProfile;
    myOptionsTable = new ScopesOrderTable();
    myProject = project;
    reloadScopeList();

    final JPanel listPanel = ToolbarDecorator.createDecorator(myOptionsTable)
      .setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          myOptionsTable.moveDown();
        }
      })
      .setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          myOptionsTable.moveUp();
        }
      })
      .addExtraAction(new AnActionButton(CodeInsightBundle.messagePointer("action.AnActionButton.text.edit.scopes"), AllIcons.Actions.Edit) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new ScopeChooserConfigurable(project));
          reloadScopeList();
        }
      })
      .disableRemoveAction()
      .disableAddAction()
      .createPanel();
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
    myOptionsTable.updateItems(scopes);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    final int size = myOptionsTable.getModel().getRowCount();
    final List<String> newScopeOrder = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      final NamedScope namedScope = myOptionsTable.getScopeAt(i);
      assert namedScope != null;
      newScopeOrder.add(namedScope.getScopeId());
    }
    if (!newScopeOrder.equals(myInspectionProfile.getScopesOrder())) {
      myInspectionProfile.setScopesOrder(newScopeOrder);
    }
    super.doOKAction();
  }
}
