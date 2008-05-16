package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public abstract class AbstractProjectTreeStructure extends ProjectAbstractTreeStructureBase implements ViewSettings {
  private final AbstractTreeNode myRoot;

  public AbstractProjectTreeStructure(Project project) {
    super(project);
    myRoot = createRoot(project, this);
  }

  protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
    return new ProjectViewProjectNode(myProject, this);
  }

  public abstract boolean isShowMembers();

  public final Object getRootElement() {
    return myRoot;
  }

  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  public final boolean hasSomethingToCommit() {
    if (myProject.isDisposed()) return false;
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  public boolean isStructureView() {
    return false;
  }

}