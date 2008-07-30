/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ProjectTopics;
import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;

public class FavoritesViewTreeBuilder extends BaseProjectTreeBuilder {
  private ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private FileStatusListener myFileStatusListener;
  private CopyPasteUtil.DefaultCopyPasteListener myCopyPasteListener;
  private final FavoritesManager.FavoritesListener myFavoritesListener;
  private final String myListName;

  public FavoritesViewTreeBuilder(Project project,
                                  JTree tree,
                                  DefaultTreeModel treeModel,
                                  ProjectAbstractTreeStructureBase treeStructure,
                                  final String name) {
    super(project, tree, treeModel, treeStructure, null);
    myListName = name;
    final MessageBusConnection connection = myProject.getMessageBus().connect(this);
    myPsiTreeChangeListener = new ProjectViewPsiTreeChangeListener(myProject) {
      protected DefaultMutableTreeNode getRootNode() {
        return FavoritesViewTreeBuilder.this.getRootNode();
      }

      protected AbstractTreeUpdater getUpdater() {
        return FavoritesViewTreeBuilder.this.getUpdater();
      }

      protected boolean isFlattenPackages() {
        return ((FavoritesTreeStructure)getTreeStructure()).isFlattenPackages();
      }

      protected void childrenChanged(PsiElement parent, final boolean stopProcessingForThisModificationCount) {
        if (findNodeByElement(parent) == null){
          getUpdater().addSubtreeToUpdate(getRootNode());
        } else {
          super.childrenChanged(parent, true);
        }
      }
    };
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        getUpdater().addSubtreeToUpdate(getRootNode());
      }
    });
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    myFileStatusListener = new MyFileStatusListener();
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    myCopyPasteListener = new CopyPasteUtil.DefaultCopyPasteListener(getUpdater());
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);

    myFavoritesListener = new FavoritesManager.FavoritesListener() {
      public void rootsChanged(String listName) {
        if (myListName.equals(listName)) {
          updateFromRoot();
        }
      }

      public void listAdded(String listName) {
        updateFromRoot();
      }

      public void listRemoved(String listName) {
        updateFromRoot();
      }
    };
    FavoritesManager.getInstance(myProject).addFavoritesListener(myFavoritesListener);
    initRootNode();
  }


  public void updateFromRoot() {
    ((FavoritesTreeStructure)getTreeStructure()).rootsChanged();
    if (isDisposed()) return;
    getUpdater().cancelAllRequests();
    super.updateFromRoot();
  }

  public void select(Object element, VirtualFile file, boolean requestFocus) {
    final DefaultMutableTreeNode node = findSmartFirstLevelNodeByElement(element);
    if (node != null){
      TreeUtil.selectInTree(node, requestFocus, getTree());
      return;
    }
    super.select(element, file, requestFocus);
  }

  @Nullable
  private static DefaultMutableTreeNode findFirstLevelNodeWithObject(final DefaultMutableTreeNode aRoot, final Object aObject) {
    for (int i = 0; i < aRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)aRoot.getChildAt(i);
      Object userObject = child.getUserObject();
      if (userObject instanceof FavoritesTreeNodeDescriptor) {
        if (Comparing.equal(((FavoritesTreeNodeDescriptor)userObject).getElement(), aObject)) {
          return child;
        }
      }
    }
    return null;
  }

  protected Object findNodeByElement(Object element) {
    final Object node = findSmartFirstLevelNodeByElement(element);
    if (node != null) return node;
    return super.findNodeByElement(element);
  }

  @Nullable
  DefaultMutableTreeNode findSmartFirstLevelNodeByElement(final Object element) {
    final Collection<AbstractTreeNode> favorites = ((AbstractTreeNode)((FavoritesTreeStructure)getTreeStructure()).getRootElement()).getChildren();
    for (AbstractTreeNode favorite : favorites) {
      Object currentValue = favorite.getValue();
      if (currentValue instanceof SmartPsiElementPointer){
        currentValue = ((SmartPsiElementPointer)favorite.getValue()).getElement();
      } /*else if (currentValue instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)currentValue).getClasses();
        if (classes.length > 0) {
          currentValue = classes[0];
        }
      }*/
      if (Comparing.equal(element, currentValue)){
        final DefaultMutableTreeNode nodeWithObject = findFirstLevelNodeWithObject((DefaultMutableTreeNode)getTree().getModel().getRoot(), favorite);
        if (nodeWithObject != null){
          return nodeWithObject;
        }
      }
    }
    return null;
  }

  public final void dispose() {
    super.dispose();
    FavoritesManager.getInstance(myProject).removeFavoritesListener(myFavoritesListener);

    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object[] childElements = getTreeStructure().getChildElements(nodeDescriptor);
    return childElements != null && childElements.length > 0;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null;
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      getUpdater().addSubtreeToUpdate(getRootNode());
    }

    public void fileStatusChanged(@NotNull VirtualFile vFile) {
      PsiElement element;
      PsiManager psiManager = PsiManager.getInstance(myProject);
      if (vFile.isDirectory()) {
        element = psiManager.findDirectory(vFile);
      }
      else {
        element = psiManager.findFile(vFile);
      }

      if (!getUpdater().addSubtreeToUpdateByElement(element) && element instanceof PsiFile && ((PsiFile) element).getFileType() == StdFileTypes.JAVA) {
        getUpdater().addSubtreeToUpdateByElement(((PsiFile)element).getContainingDirectory());
      }
    }
  }

}

