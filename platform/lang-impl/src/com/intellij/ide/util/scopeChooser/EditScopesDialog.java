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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 03-Jul-2006
 */
public class EditScopesDialog extends SettingsDialog {
  private NamedScope mySelectedScope;
  private final Project myProject;
  private final ScopeChooserConfigurable myConfigurable;
  private final boolean myCheckShared;

  public EditScopesDialog(final Project project,
                          final ScopeChooserConfigurable configurable,
                          final boolean checkShared) {
    super(project, "scopes", configurable, true, false);
    myProject = project;
    myConfigurable = configurable;
    myCheckShared = checkShared;
  }

  @Override
  public void doOKAction() {
    Object selectedObject = myConfigurable.getSelectedObject();
    mySelectedScope = selectedObject instanceof NamedScope ? (NamedScope)selectedObject : null;

    super.doOKAction();
    if (myCheckShared && mySelectedScope != null) {
      final Project project = myProject;
      final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
      NamedScope scope = manager.getScope(mySelectedScope.getName());
      if (scope == null) {
        if (Messages.showYesNoDialog(IdeBundle.message("scope.unable.to.save.scope.message"),
                                     IdeBundle.message("scope.unable.to.save.scope.title"), Messages.getErrorIcon()) == Messages.YES) {
          final String newName = Messages.showInputDialog(project, IdeBundle.message("add.scope.name.label"),
                                                          IdeBundle.message("scopes.save.dialog.title.shared"), Messages.getQuestionIcon(),
                                                          mySelectedScope.getName(), new InputValidator() {
            @Override
            public boolean checkInput(String inputString) {
              return inputString != null && inputString.length() > 0 && manager.getScope(inputString) == null;
            }

            @Override
            public boolean canClose(String inputString) {
              return checkInput(inputString);
            }
          });
          if (newName != null) {
            final PackageSet packageSet = mySelectedScope.getValue();
            scope = new NamedScope(newName, packageSet != null ? packageSet.createCopy() : null);
            mySelectedScope = scope;
            manager.addScope(mySelectedScope);
          }
        }
      }
    }
  }


  public static EditScopesDialog showDialog(final Project project, @Nullable final String scopeToSelect) {
    return showDialog(project, scopeToSelect, false);
  }

  public static EditScopesDialog showDialog(final Project project, @Nullable final String scopeToSelect, final boolean checkShared) {
    final ScopeChooserConfigurable configurable = new ScopeChooserConfigurable(project);
    final EditScopesDialog dialog = new EditScopesDialog(project, configurable, checkShared);
    if (scopeToSelect != null) {
      configurable.selectNodeInTree(scopeToSelect);
    }
    dialog.show();
    return dialog;
  }

  public NamedScope getSelectedScope() {
    return mySelectedScope;
  }
}
