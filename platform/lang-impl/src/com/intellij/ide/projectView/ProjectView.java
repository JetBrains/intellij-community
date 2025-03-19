// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class ProjectView {
  /**
   * Use this key to get unloaded modules which content roots are selected in Project View
   */
  public static final DataKey<List<UnloadedModuleDescription>> UNLOADED_MODULES_CONTEXT_KEY = DataKey.create("context.unloaded.modules.list");

  public static ProjectView getInstance(@NotNull Project project) {
    return project.getService(ProjectView.class);
  }

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public abstract @NotNull ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus);

  /**
   * Changes currently selected view and subview (if any).
   * <p>
   * When default subview is requested:<br/>
   * - if the view had never been selected then the first subview is selected <br/>
   * - otherwise subview won't be changed
   * <p>
   * It's an error when a view has no subviews and {@code subId} is not null.
   *
   * @param viewId id of view to be selected
   * @param subId  id of subview to be selected
   * @return callback which will be set to {@link ActionCallback#setDone done} if new content was selected
   * or to {@link ActionCallback#setRejected rejected} if content didn't change.
   */
  public abstract @NotNull ActionCallback changeViewCB(@NotNull String viewId, @Nullable("default subview") String subId);

  public abstract @Nullable PsiElement getParentOfCurrentSelection();

  // show pane identified by id using default(or currently selected) subId
  public abstract void changeView(@NotNull String viewId);

  /**
   * @see #changeViewCB(String, String)
   */
  public abstract void changeView(@NotNull String viewId, @Nullable String subId);

  public abstract void refresh();

  public abstract boolean isAutoscrollToSource(String paneId);

  public abstract boolean isFlattenPackages(String paneId);

  public boolean isFoldersAlwaysOnTop(String paneId) {
    return true;
  }

  public abstract boolean isShowMembers(String paneId);

  public abstract boolean isHideEmptyMiddlePackages(String paneId);

  public abstract void setHideEmptyPackages(@NotNull String paneId, boolean hideEmptyPackages);

  public boolean isUseFileNestingRules(String paneId) {
    return false;
  }

  public void setUseFileNestingRules(boolean useFileNestingRules) {
  }

  public boolean isCompactDirectories(String paneId) {
    return false;
  }

  public void setCompactDirectories(@NotNull String paneId, boolean compactDirectories) {
  }

  public boolean isShowExcludedFiles(String paneId) {
    return true;
  }

  public boolean isShowVisibilityIcons(String paneId) {
    return false;
  }

  public abstract boolean isShowLibraryContents(String paneId);

  public abstract void setShowLibraryContents(@NotNull String paneId, boolean showLibraryContents);

  public abstract boolean isShowModules(String paneId);

  public abstract void setShowModules(@NotNull String paneId, boolean showModules);

  public abstract boolean isFlattenModules(String paneId);

  public abstract void setFlattenModules(@NotNull String paneId, boolean flattenModules);

  public abstract boolean isShowURL(String paneId);

  public abstract boolean isShowScratchesAndConsoles(String paneId);

  public abstract void addProjectPane(@NotNull AbstractProjectViewPane pane);

  public abstract void removeProjectPane(@NotNull AbstractProjectViewPane pane);

  public abstract AbstractProjectViewPane getProjectViewPaneById(String id);

  public abstract boolean isAutoscrollFromSource(String paneId);

  public abstract boolean isAbbreviatePackageNames(String paneId);

  public abstract void setAbbreviatePackageNames(@NotNull String paneId, boolean abbreviatePackageNames);

  /**
   * e.g. {@link com.intellij.ide.projectView.impl.ProjectViewPane#ID}
   * @see AbstractProjectViewPane#getId()
   */
  public abstract String getCurrentViewId();

  public abstract void selectPsiElement(@NotNull PsiElement element, boolean requestFocus);

  public abstract boolean isManualOrder(String paneId);

  public abstract void setManualOrder(@NotNull String paneId, boolean enabled);

  public abstract boolean isSortByType(String paneId);

  public abstract void setSortByType(@NotNull String paneId, final boolean sortByType);

  public abstract @NotNull NodeSortKey getSortKey(String paneId);

  public abstract void setSortKey(@NotNull String paneId, @NotNull NodeSortKey sortKey);

  public abstract AbstractProjectViewPane getCurrentProjectViewPane();

  public abstract @NotNull Collection<String> getPaneIds();

  public abstract @NotNull Collection<SelectInTarget> getSelectInTargets();

  public abstract @NotNull String getDefaultViewId();
}
