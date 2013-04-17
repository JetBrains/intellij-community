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
import com.intellij.ide.util.scopeChooser.IgnoringComboBox;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

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
        final ScopeWrapper scope = (ScopeWrapper)myScopes.getSelectedItem();
        rebuildModel(project, scope != null ? scope.getName() : null);
      }
    };

    myNamedScopeManager = NamedScopeManager.getInstance(project);
    myNamedScopeManager.addScopeListener(myScopeListener);

    myValidationManager = DependencyValidationManager.getInstance(project);
    myValidationManager.addScopeListener(myScopeListener);

    myScopes.setRenderer(new ListCellRendererWrapper<ScopeWrapper>(){
      @Override
      public void customize(JList list, ScopeWrapper value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
        if (value.isSeparator()) {
          setSeparator();
        }
      }
    });
    myScopes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        rebuildWithAlarm(ScopeBasedTodosPanel.this.myAlarm);
        final ScopeWrapper selectedItem = (ScopeWrapper)myScopes.getSelectedItem();
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
    final ArrayList<ScopeWrapper> scopes = new ArrayList<ScopeWrapper>();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);

    scopes.add(new ScopeWrapper("Predefined Scopes"));
    List<NamedScope> predefinedScopesList = manager.getPredefinedScopes();
    NamedScope[] predefinedScopes = predefinedScopesList.toArray(new NamedScope[predefinedScopesList.size()]);
    predefinedScopes = NonProjectFilesScope.removeFromList(predefinedScopes);
    for (NamedScope predefinedScope : predefinedScopes) {
      scopes.add(new ScopeWrapper(predefinedScope));
    }

    collectEditableScopes(scopes, manager, "Custom Project Scopes");
    collectEditableScopes(scopes, NamedScopeManager.getInstance(project), "Custom Local Scopes");

    myScopes.setModel(new DefaultComboBoxModel(scopes.toArray(new ScopeWrapper[scopes.size()])));
    setSelection(scopeName, scopes);
  }

  private void setSelection(@Nullable String scopeName, ArrayList<ScopeWrapper> scopes) {
    boolean hasNonSeparators = false;
    for (ScopeWrapper scope : scopes) {
      if (!scope.isSeparator()) {
        hasNonSeparators = true;
        if (scopeName == null || scopeName.equals(scope.getName())) {
          myScopes.setSelectedItem(scope);
          return;
        }
      }
    }
    assert hasNonSeparators;
    setSelection(null, scopes);
  }

  private static void collectEditableScopes(ArrayList<ScopeWrapper> scopes, NamedScopesHolder manager, String separatorTitle) {
    NamedScope[] editableScopes = manager.getEditableScopes();
    if (editableScopes.length > 0) {
      scopes.add(new ScopeWrapper(separatorTitle));
      for (NamedScope scope : editableScopes) {
        scopes.add(new ScopeWrapper(scope));
      }
    }
  }

  @Override
  protected JComponent createCenterComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    final JComponent component = super.createCenterComponent();
    panel.add(component, BorderLayout.CENTER);
    myScopes = new IgnoringComboBox() {
      @Override
      protected boolean isIgnored(Object item) {
        return item instanceof ScopeWrapper && ((ScopeWrapper)item).isSeparator();
      }
    };

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

  @Override
  protected TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project) {
    ScopeBasedTodosTreeBuilder builder = new ScopeBasedTodosTreeBuilder(tree, treeModel, project, myScopes);
    builder.init();
    return builder;
  }

  public static class ScopeWrapper {
    private final String myName;
    private final boolean mySeparator;
    private NamedScope myNamedScope;

    private ScopeWrapper(NamedScope namedScope) {
      mySeparator = false;
      myNamedScope = namedScope;
      myName = myNamedScope.getName();
    }
    private ScopeWrapper(String name) {
      mySeparator = true;
      myName = name;
    }

    public String getName() {
      return myName;
    }

    public NamedScope getNamedScope() {
      return myNamedScope;
    }

    public boolean isSeparator() {
      return mySeparator;
    }
  }
}