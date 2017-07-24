/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.impl;

import com.intellij.ide.CompositeSelectInTarget;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper implements CompositeSelectInTarget {
  private String mySubId;

  protected ProjectViewSelectInTarget(Project project) {
    super(project);
  }

  @Override
  protected final void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    select(myProject, selector, getMinorViewId(), mySubId, virtualFile, requestFocus);
  }

  @NotNull
  public static ActionCallback select(@NotNull Project project,
                                      final Object toSelect,
                                      @Nullable final String viewId,
                                      @Nullable final String subviewId,
                                      final VirtualFile virtualFile,
                                      final boolean requestFocus) {
    final ActionCallback result = new ActionCallback();

    final ProjectView projectView = ProjectView.getInstance(project);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AbstractProjectViewPane pane = projectView.getProjectViewPaneById(ProjectViewPane.ID);
      pane.select(toSelect, virtualFile, requestFocus);
      return result;
    }

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    final ToolWindow projectViewToolWindow = windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    final Runnable runnable = () -> {
      Runnable r = () -> projectView.selectCB(toSelect, virtualFile, requestFocus).notify(result);
      projectView.changeViewCB(ObjectUtils.chooseNotNull(viewId, ProjectViewPane.ID), subviewId).doWhenProcessed(r);
    };

    if (requestFocus) {
      projectViewToolWindow.activate(runnable, true);
    }
    else {
      projectViewToolWindow.show(runnable);
    }

    return result;
  }

  @Override
  @NotNull
  public Collection<SelectInTarget> getSubTargets(@NotNull SelectInContext context) {
    List<SelectInTarget> result = new ArrayList<>();
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    int index = 0;
    for (String subId : pane.getSubIds()) {
      result.add(new ProjectSubViewSelectInTarget(this, subId, index++));
    }
    return result;
  }

  public boolean isSubIdSelectable(String subId, SelectInContext context) {
    return false;
  }

  @Override
  protected boolean canSelect(PsiFileSystemItem file) {
    return true;
  }

  public String getSubIdPresentableName(String subId) {
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    return pane.getPresentableSubIdName(subId);
  }

  @Override
  public void select(PsiElement element, final boolean requestFocus) {
    PsiElement toSelect = null;
    for (TreeStructureProvider provider : getProvidersDumbAware()) {
      if (provider instanceof SelectableTreeStructureProvider) {
        toSelect = ((SelectableTreeStructureProvider) provider).getTopLevelElement(element);
      }
      if (toSelect != null) break;
    }

    toSelect = findElementToSelect(element, toSelect);

    if (toSelect != null) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(toSelect);
      select(toSelect, virtualFile, requestFocus);
    }
  }

  private TreeStructureProvider[] getProvidersDumbAware() {
    TreeStructureProvider[] allProviders = Extensions.getExtensions(TreeStructureProvider.EP_NAME, myProject);
    List<TreeStructureProvider> dumbAware = DumbService.getInstance(myProject).filterByDumbAwareness(allProviders);
    return dumbAware.toArray(new TreeStructureProvider[dumbAware.size()]);
  }

  @Override
  public final String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  public final void setSubId(String subId) {
    mySubId = subId;
  }

  public final String getSubId() {
    return mySubId;
  }
}