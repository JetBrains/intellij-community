// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.treeStructure.ProjectViewUpdateCause;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

import static com.intellij.util.ObjectUtils.notNull;

abstract class ProjectViewPsiTreeChangeListener extends PsiTreeChangeAdapter {
  private final PsiModificationTracker myModificationTracker;
  private long myModificationCount;

  protected ProjectViewPsiTreeChangeListener(@NotNull Project project) {
    myModificationTracker = PsiManager.getInstance(project).getModificationTracker();
    myModificationCount = myModificationTracker.getModificationCount();
  }

  protected abstract boolean isFlattenPackages();

  protected abstract DefaultMutableTreeNode getRootNode();

  @Override
  public final void childRemoved(@NotNull PsiTreeChangeEvent event) {
    PsiElement child = event.getOldChild();
    if (child instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent(), true);
  }

  @Override
  public final void childAdded(@NotNull PsiTreeChangeEvent event) {
    PsiElement child = event.getNewChild();
    if (child instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent(), true);
  }

  @Override
  public final void childReplaced(@NotNull PsiTreeChangeEvent event) {
    PsiElement oldChild = event.getOldChild();
    PsiElement newChild = event.getNewChild();
    if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent(), true);
  }

  @Override
  public final void childMoved(@NotNull PsiTreeChangeEvent event) {
    childrenChanged(event.getOldParent(), false);
    childrenChanged(event.getNewParent(), true);
  }

  @Override
  public final void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    childrenChanged(event.getParent(), true);
  }

  protected void childrenChanged(PsiElement parent, final boolean stopProcessingForThisModificationCount) {
    if (parent instanceof PsiDirectory && isFlattenPackages()){
      addSubtreeToUpdateByRoot(ProjectViewUpdateCause.PSI_FLATTEN_PACKAGES);
      return;
    }

    long newModificationCount = myModificationTracker.getModificationCount();
    if (newModificationCount == myModificationCount) return;
    if (stopProcessingForThisModificationCount) {
      myModificationCount = newModificationCount;
    }

    while (true) {
      if (parent == null) break;
      if (parent instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)parent).getVirtualFile();
        if (virtualFile != null && !FileTypeRegistry.getInstance().isFileOfType(virtualFile, FileTypes.PLAIN_TEXT)) {
          // adding a class within a file causes a new node to appear in project view => entire dir should be updated
          parent = ((PsiFile)parent).getContainingDirectory();
          if (parent == null) break;
        }
      }
      else if (parent instanceof PsiDirectory &&
               ScratchUtil.isScratch(((PsiDirectory)parent).getVirtualFile())) {
        addSubtreeToUpdateByRoot(ProjectViewUpdateCause.PSI_SCRATCH);
        break;
      }

      if (addSubtreeToUpdateByElementFile(parent, ProjectViewUpdateCause.PSI_CHILDREN)) {
        break;
      }

      if (parent instanceof PsiFile || parent instanceof PsiDirectory) break;
      parent = parent.getParent();
    }
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    PsiElement element = event.getElement();
    switch (propertyName) {
      case PsiTreeChangeEvent.PROP_ROOTS, PsiTreeChangeEvent.PROP_FILE_TYPES, PsiTreeChangeEvent.PROP_UNLOADED_PSI -> 
        addSubtreeToUpdateByRoot(ProjectViewUpdateCause.PSI_PROPERTY);
      case PsiTreeChangeEvent.PROP_WRITABLE -> {
        if (!addSubtreeToUpdateByElementFile(element, ProjectViewUpdateCause.PSI_PROPERTY) && element instanceof PsiFile) {
          addSubtreeToUpdateByElementFile(((PsiFile)element).getContainingDirectory(), ProjectViewUpdateCause.PSI_PROPERTY);
        }
      }
      case PsiTreeChangeEvent.PROP_FILE_NAME, PsiTreeChangeEvent.PROP_DIRECTORY_NAME -> {
        if (element instanceof PsiDirectory && isFlattenPackages()) {
          addSubtreeToUpdateByRoot(ProjectViewUpdateCause.PSI_FLATTEN_PACKAGES);
          return;
        }
        final PsiElement parent = element.getParent();
        if (parent == null || !addSubtreeToUpdateByElementFile(parent, ProjectViewUpdateCause.PSI_PROPERTY)) {
          addSubtreeToUpdateByElementFile(element, ProjectViewUpdateCause.PSI_PROPERTY);
        }
      }
    }
  }

  protected void addSubtreeToUpdateByRoot(@NotNull ProjectViewUpdateCause cause) {
  }

  protected boolean addSubtreeToUpdateByElement(@NotNull PsiElement element, @NotNull ProjectViewUpdateCause cause) {
    return false;
  }

  private boolean addSubtreeToUpdateByElementFile(PsiElement element, @NotNull ProjectViewUpdateCause cause) {
    return element != null && addSubtreeToUpdateByElement(notNull(element.getContainingFile(), element), cause);
  }
}
