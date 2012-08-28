/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 27-Jul-2007
 */
package com.intellij.ide.todo;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ScopeBasedTodosPanel extends TodoPanel {
  private static final String SELECTED_SCOPE = "TODO_SCOPE";
  private final Alarm myAlarm;
  private JComboBox myScopes;
  private final NamedScopesHolder.ScopeListener myScopeListener;
  private final NamedScopeManager myNamedScopeManager;
  private final DependencyValidationManager myValidationManager;

  public ScopeBasedTodosPanel(final Project project, TodoPanelSettings settings, Content content){
    super(project,settings,false,content);
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
    final String scopeName = PropertiesComponent.getInstance(project).getValue(SELECTED_SCOPE);
    rebuildModel(project, scopeName);

    myScopeListener = new NamedScopesHolder.ScopeListener() {
      @Override
      public void scopesChanged() {
        final NamedScope scope = (NamedScope)myScopes.getSelectedItem();
        rebuildModel(project, scope != null ? scope.getName() : null);
      }
    };

    myNamedScopeManager = NamedScopeManager.getInstance(project);
    myNamedScopeManager.addScopeListener(myScopeListener);

    myValidationManager = DependencyValidationManager.getInstance(project);
    myValidationManager.addScopeListener(myScopeListener);

    myScopes.setRenderer(new ListCellRendererWrapper<NamedScope>(myScopes){
      @Override
      public void customize(JList list, NamedScope value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    });
    myScopes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        rebuildWithAlarm(ScopeBasedTodosPanel.this.myAlarm);
        final NamedScope selectedItem = (NamedScope)myScopes.getSelectedItem();
        if (selectedItem != null) {
          PropertiesComponent.getInstance(myProject).setValue(SELECTED_SCOPE, selectedItem.getName());
        }
      }
    });
    rebuildWithAlarm(myAlarm);
  }

  @Override
  public void dispose() {
    myNamedScopeManager.removeScopeListener(myScopeListener);
    myValidationManager.removeScopeListener(myScopeListener);
    super.dispose();
  }

  private void rebuildModel(Project project, String scopeName) {
    NamedScope[] scopes = DependencyValidationManager.getInstance(project).getScopes();
    scopes = ArrayUtil.mergeArrays(scopes, NamedScopeManager.getInstance(project).getScopes());
    scopes = NonProjectFilesScope.removeFromList(scopes);
    myScopes.setModel(new DefaultComboBoxModel(scopes));
    if (scopeName != null) {
      for (NamedScope scope : scopes) {
        if (Comparing.strEqual(scopeName, scope.getName())) {
          myScopes.setSelectedItem(scope);
          break;
        }
      }
    }
  }

  @Override
  protected JComponent createCenterComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    final JComponent component = super.createCenterComponent();
    panel.add(component, BorderLayout.CENTER);
    myScopes = new JComboBox();
    
    JPanel chooserPanel = new JPanel(new GridBagLayout());
    final JLabel scopesLabel = new JLabel("Scope:");
    scopesLabel.setDisplayedMnemonic('S');
    scopesLabel.setLabelFor(myScopes);
    final GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                             new Insets(2, 2, 2, 2), 0, 0);
    chooserPanel.add(scopesLabel, gc);
    chooserPanel.add(myScopes, gc);
    
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    chooserPanel.add(Box.createHorizontalBox(), gc);
    panel.add(chooserPanel, BorderLayout.NORTH);
    return panel;
  }

  protected TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project) {
    ScopeBasedTodosTreeBuilder builder = new ScopeBasedTodosTreeBuilder(tree, treeModel, project, myScopes);
    builder.init();
    return builder;
  }
}