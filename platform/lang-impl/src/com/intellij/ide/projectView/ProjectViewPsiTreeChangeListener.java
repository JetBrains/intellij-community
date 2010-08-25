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

import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;

import javax.swing.tree.DefaultMutableTreeNode;

public abstract class ProjectViewPsiTreeChangeListener extends PsiTreeChangeAdapter {
  private final FileTypeManager myFileTypeManager;
  private final PsiModificationTracker myModificationTracker;
  private long myOutOfCodeBlockModificationCount;

  protected ProjectViewPsiTreeChangeListener(Project project) {
    myFileTypeManager = FileTypeManager.getInstance();
    myModificationTracker = PsiManager.getInstance(project).getModificationTracker();
    myOutOfCodeBlockModificationCount = myModificationTracker.getOutOfCodeBlockModificationCount();
  }

  protected abstract AbstractTreeUpdater getUpdater();

  protected abstract boolean isFlattenPackages();

  protected abstract DefaultMutableTreeNode getRootNode();

  public final void childRemoved(PsiTreeChangeEvent event) {
    PsiElement child = event.getOldChild();
    if (child instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent(), true);
  }

  public final void childAdded(PsiTreeChangeEvent event) {
    PsiElement child = event.getNewChild();
    if (child instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent(), true);
  }

  public final void childReplaced(PsiTreeChangeEvent event) {
    PsiElement oldChild = event.getOldChild();
    PsiElement newChild = event.getNewChild();
    if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent(), true);
  }

  public final void childMoved(PsiTreeChangeEvent event) {
    childrenChanged(event.getOldParent(), false);
    childrenChanged(event.getNewParent(), true);
  }

  public final void childrenChanged(PsiTreeChangeEvent event) {
    childrenChanged(event.getParent(), true);
  }

  protected void childrenChanged(PsiElement parent, final boolean stopProcessingForThisModificationCount) {
    if (parent instanceof PsiDirectory && isFlattenPackages()){
      getUpdater().addSubtreeToUpdate(getRootNode());
      return;
    }

    long newModificationCount = myModificationTracker.getOutOfCodeBlockModificationCount();
    if (newModificationCount == myOutOfCodeBlockModificationCount) return;
    if (stopProcessingForThisModificationCount) {
      myOutOfCodeBlockModificationCount = newModificationCount;
    }

    while(true){
      if (parent == null) break;
      if (parent instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)parent).getVirtualFile();
        if (virtualFile != null && myFileTypeManager.getFileTypeByFile(virtualFile) != FileTypes.PLAIN_TEXT) {
          // adding a class within a file causes a new node to appear in project view => entire dir should be updated
          parent = ((PsiFile)parent).getContainingDirectory();
          if (parent == null) break;
        }
      }

      if (getUpdater().addSubtreeToUpdateByElement(parent)){
        break;
      }

      if (parent instanceof PsiFile ||
          parent instanceof PsiDirectory) break;
      parent = parent.getParent();
    }
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    PsiElement element = event.getElement();
    DefaultMutableTreeNode rootNode = getRootNode();
    AbstractTreeUpdater updater = getUpdater();
    if (propertyName.equals(PsiTreeChangeEvent.PROP_ROOTS)) {
      updater.addSubtreeToUpdate(rootNode);
    }
    else if (propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
      if (!updater.addSubtreeToUpdateByElement(element) && element instanceof PsiFile) {
        updater.addSubtreeToUpdateByElement(((PsiFile)element).getContainingDirectory());
      }
    }
    else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME) || propertyName.equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)){
      if (element instanceof PsiDirectory && isFlattenPackages()){
        updater.addSubtreeToUpdate(rootNode);
        return;
      }

      updater.addSubtreeToUpdateByElement(element);
    }
    else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_TYPES)){
      updater.addSubtreeToUpdate(rootNode);
    }
  }
}
