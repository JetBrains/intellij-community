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
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class PackageSetChooserCombo extends ComponentWithBrowseButton<JComponent> {
  private static final Logger LOG = Logger.getInstance(PackageSetChooserCombo.class);

  private final Project myProject;

  public PackageSetChooserCombo(final Project project, final String preselect) {
    this(project, preselect, true, true);
  }

  public PackageSetChooserCombo(final Project project, @Nullable final String preselect, final boolean enableBrowseButton, final boolean useCombo) {
    super(useCombo ? new JComboBox() : new JBComboBoxTableCellEditorComponent(), null);
    myProject = project;

    final JComponent component = getChildComponent();
    if (component instanceof JComboBox) {
      component.setBorder(null);
    }

    if (enableBrowseButton) {
      addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final NamedScope scope;
          if (component instanceof JComboBox) {
            scope = (NamedScope)((JComboBox)component).getSelectedItem();
          }
          else {
            scope = (NamedScope)((JBComboBoxTableCellEditorComponent)component).getEditorValue();
          }
          if (scope instanceof NamedScope.UnnamedScope) {
            @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
            final Map<String, PackageSet> unnamedScopes = DependencyValidationManager.getInstance(myProject).getUnnamedScopes();
            final EditUnnamedScopesDialog dlg = new EditUnnamedScopesDialog(scope);
            if (dlg.showAndGet()) {
              final PackageSet packageSet = scope.getValue();
              LOG.assertTrue(packageSet != null);
              unnamedScopes.remove(packageSet.getText());
              final PackageSet editedScope = dlg.getScope();
              if (editedScope != null) {
                unnamedScopes.put(editedScope.getText(), editedScope);
              }
              rebuild();
              if (editedScope != null) {
                selectScope(editedScope.getText());
              }
            }
          }
          else {
            final EditScopesDialog dlg = EditScopesDialog.showDialog(myProject, scope.getName(), true);
            if (dlg.isOK()) {
              rebuild();
              final NamedScope namedScope = dlg.getSelectedScope();
              if (namedScope != null) {
                selectScope(namedScope.getName());
              }
            }
          }
        }
      });
    } else {
      getButton().setVisible(false);
    }

    if (component instanceof JComboBox) {
      ((JComboBox)component).setRenderer(new ListCellRendererWrapper<NamedScope>() {
        @Override
        public void customize(JList list, NamedScope value, int index, boolean selected, boolean hasFocus) {
          setText(value == null ? "" : value.getName());
        }
      });
    }
    else {
      ((JBComboBoxTableCellEditorComponent)component).setToString(o -> o == null ? "" : ((NamedScope)o).getName());
    }

    rebuild();

    selectScope(preselect);
  }

  private void selectScope(final String preselect) {
    final JComponent component = getChildComponent();
    if (preselect != null) {
      if (component instanceof JComboBox) {
        final DefaultComboBoxModel model = (DefaultComboBoxModel)((JComboBox)component).getModel();
        for (int i = 0; i < model.getSize(); i++) {
          final NamedScope descriptor = (NamedScope)model.getElementAt(i);
          if (preselect.equals(descriptor.getName())) {
            ((JComboBox)component).setSelectedIndex(i);
            break;
          }
        }
      }
      else {
        final Object[] options = ((JBComboBoxTableCellEditorComponent)component).getOptions();
        for (Object option : options) {
          final NamedScope descriptor = (NamedScope)option;
          if (preselect.equals(descriptor.getName())) {
            ((JBComboBoxTableCellEditorComponent)component).setDefaultValue(descriptor);
            break;
          }
        }
      }
    }
  }

  private void rebuild() {
    final JComponent component = getChildComponent();
    final NamedScope[] model = createModel();
    if (component instanceof JComboBox) {
      ((JComboBox)component).setModel(new DefaultComboBoxModel(model));
    }
    else {
      ((JBComboBoxTableCellEditorComponent)component).setOptions((Object[])model);
    }
  }

  protected NamedScope[] createModel() {
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(myProject);
    final Collection<NamedScope> model = new ArrayList<>(Arrays.asList(manager.getScopes()));
    for (PackageSet unnamedScope : manager.getUnnamedScopes().values()) {
      model.add(new NamedScope.UnnamedScope(unnamedScope));
    }
    model.remove(DefaultScopesProvider.getInstance(myProject).getProblemsScope());
    return model.toArray(new NamedScope[model.size()]);
  }

  @Nullable
  public NamedScope getSelectedScope() {
    final JComponent component = getChildComponent();
    if (component instanceof JComboBox) {
      int idx = ((JComboBox)component).getSelectedIndex();
      if (idx < 0) return null;
      return (NamedScope)((JComboBox)component).getSelectedItem();
    }
    else {
      return (NamedScope)((JBComboBoxTableCellEditorComponent)component).getEditorValue();
    }
  }

  private class EditUnnamedScopesDialog extends DialogWrapper {
    private PackageSet myScope;
    private final ScopeEditorPanel myPanel;

    public EditUnnamedScopesDialog(final NamedScope scope) {
      super(PackageSetChooserCombo.this, false);
      myScope = scope.getValue();
      myPanel = new ScopeEditorPanel(myProject, DependencyValidationManager.getInstance(myProject));
      init();
      myPanel.reset(myScope, null);
    }

    @Override
    @Nullable
    protected JComponent createCenterPanel() {
      return myPanel.getPanel();
    }

    @Override
    protected void doOKAction() {
      myScope = myPanel.getCurrentScope();
      super.doOKAction();
    }

    public PackageSet getScope() {
      return myScope;
    }
  }
}