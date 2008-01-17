package com.intellij.ide.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

import java.util.Collection;

public abstract class ProjectView {
  public static ProjectView getInstance(Project project) {
    return project.getComponent(ProjectView.class);
  }

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

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

  public abstract String getCurrentViewId();

  public abstract void selectPsiElement(PsiElement element, boolean requestFocus);

  public abstract boolean isSortByType(String paneId);
  public abstract void setSortByType(String paneId, final boolean sortByType);

  public abstract AbstractProjectViewPane getCurrentProjectViewPane();

  public abstract Collection<String> getPaneIds();
}