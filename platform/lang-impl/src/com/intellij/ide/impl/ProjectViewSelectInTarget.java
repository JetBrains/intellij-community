/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper implements CompositeSelectInTarget {
  private String mySubId;

  protected ProjectViewSelectInTarget(Project project) {
    super(project);
  }

  protected final void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    select(myProject, selector, getMinorViewId(), mySubId, virtualFile, requestFocus);
  }

  public static ActionCallback select(@NotNull Project project,
                            final Object toSelect,
                            @Nullable final String viewId,
                            @Nullable final String subviewId,
                            final VirtualFile virtualFile,
                            final boolean requestFocus) {
    final ActionCallback result = new ActionCallback();


    final ProjectView projectView = ProjectView.getInstance(project);
    ToolWindowManager windowManager=ToolWindowManager.getInstance(project);
    final ToolWindow projectViewToolWindow = windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    final Runnable runnable = new Runnable() {
      public void run() {
        if (requestFocus) {
          projectView.changeView(viewId, subviewId);
        }

        projectView.selectCB(toSelect, virtualFile, requestFocus).notify(result);
      }
    };

    if (requestFocus) {
      projectViewToolWindow.activate(runnable, false);
    } else {
      projectViewToolWindow.show(runnable);
    }

    return result;
  }


  @NotNull
  public Collection<SelectInTarget> getSubTargets(SelectInContext context) {
    List<SelectInTarget> result = new ArrayList<SelectInTarget>();
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

  public void select(PsiElement element, final boolean requestFocus) {
    PsiElement toSelect = null;
    for (TreeStructureProvider provider : getProvidersDumbAware()) {
      if (provider instanceof SelectableTreeStructureProvider) {
        toSelect = ((SelectableTreeStructureProvider) provider).getTopLevelElement(element);
      }
      if (toSelect != null) break;
    }
    if (toSelect == null) {
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        toSelect = element;
      }
      else {
        final PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) return;
        final FileViewProvider viewProvider = containingFile.getViewProvider();
        toSelect = viewProvider.getPsi(viewProvider.getBaseLanguage());
      }
    }
    if (toSelect == null) return;
    PsiElement originalElement = toSelect.getOriginalElement();
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(originalElement);
    select(originalElement, virtualFile, requestFocus);
  }

  private TreeStructureProvider[] getProvidersDumbAware() {
    List<TreeStructureProvider> allProviders = Arrays.asList(Extensions.getExtensions(TreeStructureProvider.EP_NAME, myProject));
    List<TreeStructureProvider> dumbAware = DumbService.getInstance(myProject).filterByDumbAwareness(allProviders);
    return dumbAware.toArray(new TreeStructureProvider[dumbAware.size()]);
  }

  public final String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  protected boolean canWorkWithCustomObjects() {
    return true;
  }

  public final void setSubId(String subId) {
    mySubId = subId;
  }
}
