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

package com.intellij.ide.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class ProjectView {
  public static ProjectView getInstance(Project project) {
    return ServiceManager.getService(project, ProjectView.class);
  }

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public abstract ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus);

  @Nullable
  public abstract PsiElement getParentOfCurrentSelection();

  // show pane identified by id using default(or currently selected) subId
  public abstract void changeView(String viewId);
  public abstract void changeView(String viewId, String subId);

  public abstract void changeView();

  public abstract void refresh();

  public abstract boolean isAutoscrollToSource(String paneId);

  public abstract boolean isFlattenPackages(String paneId);

  public abstract boolean isShowMembers(String paneId);

  public abstract boolean isHideEmptyMiddlePackages(String paneId);

  public abstract void setHideEmptyPackages(boolean hideEmptyPackages, String paneId);

  public abstract boolean isShowLibraryContents(String paneId);

  public abstract void setShowLibraryContents(boolean showLibraryContents, String paneId);

  public abstract boolean isShowModules(String paneId);

  public abstract void setShowModules(boolean showModules, String paneId);

  public abstract void addProjectPane(final AbstractProjectViewPane pane);

  public abstract void removeProjectPane(AbstractProjectViewPane instance);

  public abstract AbstractProjectViewPane getProjectViewPaneById(String id);

  public abstract boolean isAutoscrollFromSource(String paneId);

  public abstract boolean isAbbreviatePackageNames(String paneId);

  public abstract void setAbbreviatePackageNames(boolean abbreviatePackageNames, String paneId);

  /**
   * e.g. {@link com.intellij.ide.projectView.impl.ProjectViewPane#ID}
   * @see com.intellij.ide.projectView.impl.AbstractProjectViewPane#getId()
   */
  public abstract String getCurrentViewId();

  public abstract void selectPsiElement(PsiElement element, boolean requestFocus);

  public abstract boolean isSortByType(String paneId);
  public abstract void setSortByType(String paneId, final boolean sortByType);

  public abstract AbstractProjectViewPane getCurrentProjectViewPane();

  public abstract Collection<String> getPaneIds();

  public abstract Collection<SelectInTarget> getSelectInTargets();
}
