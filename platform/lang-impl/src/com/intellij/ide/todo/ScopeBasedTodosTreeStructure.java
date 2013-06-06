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

import com.intellij.ide.todo.nodes.ToDoRootNode;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;

import javax.swing.*;
import java.util.Collection;

public class ScopeBasedTodosTreeStructure extends TodoTreeStructure {
  private final JComboBox myScopes;

  public ScopeBasedTodosTreeStructure(Project project, JComboBox scopes) {
    super(project);
    myScopes = scopes;
  }

  @Override
  public boolean accept(final PsiFile psiFile) {
    if (!psiFile.isValid()) return false;
    boolean isAffected = false;
    final ScopeBasedTodosPanel.ScopeWrapper scope = (ScopeBasedTodosPanel.ScopeWrapper)myScopes.getSelectedItem();
    if (scope != null) {
      final PackageSet value = scope.getNamedScope().getValue();
      if (value != null) {
        isAffected = value.contains(psiFile, NamedScopesHolder.getHolder(myProject, scope.getName(), DependencyValidationManager.getInstance(myProject)));
      }
    }
    return isAffected && (myTodoFilter != null && myTodoFilter.accept(mySearchHelper, psiFile) ||
                          (myTodoFilter == null && mySearchHelper.getTodoItemsCount(psiFile) > 0));
  }

  @Override
  public boolean getIsPackagesShown() {
    return myArePackagesShown;
  }

  @Override
  Object getFirstSelectableElement() {
    return ((ToDoRootNode)myRootElement).getSummaryNode();
  }

  @Override
  protected AbstractTreeNode createRootElement() {
    return new ToDoRootNode(myProject, new Object(), myBuilder, mySummaryElement);
  }
}