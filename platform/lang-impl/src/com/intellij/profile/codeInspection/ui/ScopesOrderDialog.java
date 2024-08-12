// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.profile.codeInspection.ui.table.ScopesOrderTable;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScopesOrderDialog extends DialogWrapper {
  private final ScopesOrderTable myOptionsTable;
  private final InspectionProfileImpl myInspectionProfile;
  private final @NotNull Project myProject;
  private final JPanel myPanel;

  ScopesOrderDialog(final @NotNull Component parent,
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
      .addExtraAction(new AnAction(CodeInsightBundle.messagePointer("action.AnActionButton.text.edit.scopes"), AllIcons.Actions.Edit) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new ScopeChooserConfigurable(project));
          reloadScopeList();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      })
      .disableRemoveAction()
      .disableAddAction()
      .createPanel();
    final JLabel descr = ComponentPanelBuilder.createCommentComponent(AnalysisBundle.message("inspections.settings.scopes.order.help.label"), true, 110);
    descr.setBorder(JBUI.Borders.emptyTop(5));
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

  @Override
  protected @Nullable JComponent createCenterPanel() {
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
