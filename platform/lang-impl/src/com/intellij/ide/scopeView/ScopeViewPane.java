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

package com.intellij.ide.scopeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ShowModulesAction;
import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class ScopeViewPane extends AbstractProjectViewPane {
  @NonNls public static final String ID = "Scope";
  private ScopeTreeViewPanel myViewPanel;
  private final DependencyValidationManager myDependencyValidationManager;
  private final NamedScopeManager myNamedScopeManager;
  private final NamedScopesHolder.ScopeListener myScopeListener;

  public ScopeViewPane(@NotNull Project project, DependencyValidationManager dependencyValidationManager, NamedScopeManager namedScopeManager) {
    super(project);

    myDependencyValidationManager = dependencyValidationManager;
    myNamedScopeManager = namedScopeManager;
    myScopeListener = new NamedScopesHolder.ScopeListener() {
      final Alarm refreshProjectViewAlarm = new Alarm(myProject);
      @Override
      public void scopesChanged() {
        if (refreshProjectViewAlarm.isDisposed()) {
          return;
        }

        // amortize batch scope changes
        refreshProjectViewAlarm.cancelAllRequests();
        refreshProjectViewAlarm.addRequest(() -> {
          if (myProject.isDisposed()) {
            return;
          }

          final String subId = getSubId();
          ProjectView projectView = ProjectView.getInstance(myProject);
          final String id = projectView.getCurrentViewId();
          projectView.removeProjectPane(ScopeViewPane.this);
          projectView.addProjectPane(ScopeViewPane.this);
          if (id != null) {
            if (Comparing.strEqual(id, getId())) {
              projectView.changeView(id, subId);
            }
            else {
              projectView.changeView(id);
            }
          }
        }, 10);
      }
    };
    myDependencyValidationManager.addScopeListener(myScopeListener);
    myNamedScopeManager.addScopeListener(myScopeListener);
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("scope.view.title");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @Override
  public JComponent createComponent() {
    if (myViewPanel == null) {
      myViewPanel = new ScopeTreeViewPanel(myProject);
      Disposer.register(this, myViewPanel);
      myViewPanel.initListeners();
      myTree = myViewPanel.getTree();
      CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionPlaces.SCOPE_VIEW_POPUP);
      enableDnD();
    }

    myViewPanel.selectScope(NamedScopesHolder.getScope(myProject, getSubId()));
    return myViewPanel.getPanel();
  }

  @Override
  public void dispose() {
    myViewPanel = null;
    myDependencyValidationManager.removeScopeListener(myScopeListener);
    myNamedScopeManager.removeScopeListener(myScopeListener);
    super.dispose();
  }

  @Override
  @NotNull
  public String[] getSubIds() {
    return ContainerUtil.map2Array(getShownScopes(), String.class, scope -> scope.getName());
  }

  @NotNull
  public static Collection<NamedScope> getShownScopes(@NotNull Project project) {
    return getShownScopes(DependencyValidationManager.getInstance(project), NamedScopeManager.getInstance(project));
  }

  private Collection<NamedScope> getShownScopes() {
    return getShownScopes(myDependencyValidationManager, myNamedScopeManager);
  }

  @NotNull
  private static Collection<NamedScope> getShownScopes(DependencyValidationManager dependencyValidationManager, NamedScopeManager namedScopeManager) {
    List<NamedScope> list = ContainerUtil.newArrayList();
    for (NamedScope scope : ContainerUtil.concat(dependencyValidationManager.getScopes(), namedScopeManager.getScopes())) {
      if (scope instanceof NonProjectFilesScope) continue;
      if (scope instanceof ScratchesNamedScope) continue;
      if (scope == CustomScopesProviderEx.getAllScope()) continue;
      list.add(scope);
    }
    return list;
  }

  @Override
  @NotNull
  public String getPresentableSubIdName(@NotNull final String subId) {
    return subId;
  }

  @Override
  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.add(ActionManager.getInstance().getAction("ScopeView.EditScopes"));
    actionGroup.addAction(new ShowModulesAction(myProject){
      @NotNull
      @Override
      protected String getId() {
        return ScopeViewPane.this.getId();
      }
    }).setAsSecondary(true);
    actionGroup.addAction(createFlattenModulesAction(() -> true)).setAsSecondary(true);
  }

  @NotNull
  @Override
  public ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    saveExpandedPaths();
    myViewPanel.selectScope(NamedScopesHolder.getScope(myProject, getSubId()));
    restoreExpandedPaths();
    return ActionCallback.DONE;
  }

  @Override
  public void select(Object element, VirtualFile file, boolean requestFocus) {
    if (file == null) return;
    PsiFileSystemItem psiFile = file.isDirectory() ? PsiManager.getInstance(myProject).findDirectory(file)
                                                   : PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return;
    if (!(element instanceof PsiElement)) return;

    List<NamedScope> allScopes = ContainerUtil.newArrayList(getShownScopes());
    for (NamedScope scope : allScopes) {
      String name = scope.getName();
      if (name.equals(getSubId())) {
        allScopes.remove(scope);
        allScopes.add(0, scope);
        break;
      }
    }
    for (NamedScope scope : allScopes) {
      String name = scope.getName();
      PackageSet packageSet = scope.getValue();
      if (packageSet == null) continue;
      if (changeView(packageSet, ((PsiElement)element), psiFile, name, myNamedScopeManager, requestFocus)) break;
      if (changeView(packageSet, ((PsiElement)element), psiFile, name, myDependencyValidationManager, requestFocus)) break;
    }
  }

  private boolean changeView(final PackageSet packageSet, final PsiElement element, final PsiFileSystemItem psiFileSystemItem, final String name, final NamedScopesHolder holder,
                             boolean requestFocus) {
    if ((packageSet instanceof PackageSetBase && ((PackageSetBase)packageSet).contains(psiFileSystemItem.getVirtualFile(), myProject, holder)) ||
        (psiFileSystemItem instanceof PsiFile && packageSet.contains((PsiFile)psiFileSystemItem, holder))) {
      if (!name.equals(getSubId())) {
        if (!requestFocus) return true;
        ProjectView.getInstance(myProject).changeView(getId(), name);
      }
      myViewPanel.selectNode(element, psiFileSystemItem, requestFocus);
      return true;
    }
    return false;
  }

  @Override
  public int getWeight() {
    return 3;
  }

  @Override
  public void installComparator() {
    myViewPanel.setSortByType();
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new ScopePaneSelectInTarget(myProject);
  }

  @Override
  protected Object exhumeElementFromNode(final DefaultMutableTreeNode node) {
    if (node instanceof PackageDependenciesNode) {
      return ((PackageDependenciesNode)node).getPsiElement();
    }
    return super.exhumeElementFromNode(node);
  }

  @Override
  public Object getData(final String dataId) {
    final Object data = super.getData(dataId);
    if (data != null) {
      return data;
    }
    return myViewPanel == null ? null : myViewPanel.getData(dataId);
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    final ActionCallback callback = myViewPanel.getActionCallback();
    return callback == null ? ActionCallback.DONE : callback;
  }
}
