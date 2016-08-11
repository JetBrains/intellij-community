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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ScopesOrderDialog extends DialogWrapper {

  private final JList myOptionsList = new JBList();

  private final InspectionProfileImpl myInspectionProfile;
  private final Project myProject;
  private final JPanel myPanel;

  public ScopesOrderDialog(final @NotNull Component parent,
                           final InspectionProfileImpl inspectionProfile,
                           final Project project) {
    super(parent, true);
    myInspectionProfile = inspectionProfile;
    myProject = project;

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
    }).disableRemoveAction().disableAddAction().createPanel();
    final JLabel descr = new JLabel("<html><p>If file appears in two or more scopes, it will be " +
                                           "inspected with settings of the topmost scope in list above.</p><p/>" +
                                           "<p>Scope order is set globally for all inspections in the profile.</p></html>");
    descr.setPreferredSize(JBUI.size(300, 100));
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, descr);
    myPanel = new JPanel();
    myPanel.setLayout(new BorderLayout());
    myPanel.add(listPanel, BorderLayout.CENTER);
    myPanel.add(descr, BorderLayout.SOUTH);
    fillList();
    init();
    setTitle("Scopes Order");
  }

  private void fillList() {
    DefaultListModel model = new DefaultListModel();
    model.removeAllElements();

    final List<String> scopes = new ArrayList<>();
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(myProject)) {
      for (final NamedScope scope : holder.getScopes()) {
        if (!(scope instanceof NonProjectFilesScope)) {
          scopes.add(scope.getName());
        }
      }
    }
    scopes.remove(CustomScopesProviderEx.getAllScope().getName());
    Collections.sort(scopes, new ScopeOrderComparator(myInspectionProfile));
    for (String scopeName : scopes) {
      model.addElement(scopeName);
    }
    myOptionsList.setModel(model);
    myOptionsList.setSelectedIndex(0);
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
      final String scopeName = (String) myOptionsList.getModel().getElementAt(i);
      newScopeOrder[i] = scopeName;
    }
    if (!Arrays.equals(newScopeOrder, myInspectionProfile.getScopesOrder())) {
      myInspectionProfile.setScopesOrder(newScopeOrder);
    }
    super.doOKAction();
  }
}
