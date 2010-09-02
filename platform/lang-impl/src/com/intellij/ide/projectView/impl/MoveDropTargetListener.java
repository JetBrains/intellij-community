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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.impl.nodes.DropTargetNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.move.MoveHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vladk
 */
class MoveDropTargetListener implements DropTargetListener {
  final private DataFlavor dataFlavor;
  final private Project myProject;
  final private JTree myTree;
  final private PsiRetriever myPsiRetriever;

  public interface ModifierSource {
    int getModifiers();
  }

  public MoveDropTargetListener(final PsiRetriever psiRetriever, final JTree tree, final Project project, final DataFlavor flavor) {
    myPsiRetriever = psiRetriever;
    myProject = project;
    myTree = tree;
    dataFlavor = flavor;
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    final DropHandler dropHandler = getDropHandler(dtde.getDropAction());
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    if (sourceNodes != null && dropHandler.isValidSource(sourceNodes)) {
      dtde.acceptDrag(dtde.getDropAction());
    }
    else if (dtde.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      dtde.acceptDrag(dtde.getDropAction());
    }
    else {
      dtde.rejectDrag();
    }
  }

  public void dragOver(DropTargetDragEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    final TreeNode targetNode = getTargetNode(dtde.getLocation());
    final int dropAction = dtde.getDropAction();
    if (sourceNodes != null && targetNode != null && canDrop(sourceNodes, targetNode, dropAction)) {
      dtde.acceptDrag(dropAction);
    }
    else if (targetNode != null && dtde.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      dtde.acceptDrag(dropAction);
    }
    else {
      dtde.rejectDrag();
    }
  }

  public void dropActionChanged(DropTargetDragEvent dtde) {
  }

  public void dragExit(DropTargetEvent dte) {
  }

  public void drop(DropTargetDropEvent dtde) {
    final TreeNode[] sourceNodes = getSourceNodes(dtde.getTransferable());
    final TreeNode targetNode = getTargetNode(dtde.getLocation());
    final int dropAction = dtde.getDropAction();
    if (targetNode == null || (dropAction & DnDConstants.ACTION_COPY_OR_MOVE) == 0) {
      dtde.rejectDrop();
    }
    else if (sourceNodes == null) {
      if (dtde.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        dtde.acceptDrop(dropAction);
        List<File> fileList;
        try {
          fileList = (List<File>)dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        }
        catch (Exception e) {
          dtde.rejectDrop();
          return;
        }
        getDropHandler(dropAction).doDropFiles(fileList, targetNode);
      }
      else {
        dtde.rejectDrop();
      }
    }
    else if (!doDrop(sourceNodes, targetNode, dropAction, dtde)) {
      dtde.rejectDrop();
    }
  }

  @Nullable
  private TreeNode[] getSourceNodes(final Transferable transferable) {
    if (!transferable.isDataFlavorSupported(dataFlavor)) {
      return null;
    }
    try {
      Object transferData = transferable.getTransferData(dataFlavor);
      if (transferData instanceof AbstractProjectViewPSIPane.TransferableWrapper) {
        return ((AbstractProjectViewPSIPane.TransferableWrapper)transferData).getTreeNodes();
      }
      return null;
    }
    catch (UnsupportedFlavorException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private TreeNode getTargetNode(final Point location) {
    final TreePath path = myTree.getPathForLocation(location.x, location.y);
    return path == null ? null : (TreeNode)path.getLastPathComponent();
  }

  private boolean canDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode, final int dropAction) {
    return doDrop(sourceNodes, targetNode, dropAction, null);
  }

  private boolean doDrop(@NotNull final TreeNode[] sourceNodes,
                         @NotNull final TreeNode targetNode,
                         final int dropAction,
                         @Nullable final DropTargetDropEvent dtde) {
    TreeNode validTargetNode = getValidTargetNode(sourceNodes, targetNode, dropAction);
    if (validTargetNode != null) {
      final TreeNode[] filteredSourceNodes = removeRedundantSourceNodes(sourceNodes, validTargetNode, dropAction);
      if (filteredSourceNodes.length != 0) {
        if (dtde != null) {
          dtde.dropComplete(true);
          getDropHandler(dropAction).doDrop(filteredSourceNodes, validTargetNode);
        }
        return true;
      }
    }
    return false;
  }

  @Nullable
  private TreeNode getValidTargetNode(final @NotNull TreeNode[] sourceNodes, final @NotNull TreeNode targetNode, final int dropAction) {
    final DropHandler dropHandler = getDropHandler(dropAction);
    TreeNode currentNode = targetNode;
    while (true) {
      if (dropHandler.isValidTarget(sourceNodes, currentNode)) {
        return currentNode;
      }
      if (!dropHandler.shouldDelegateToParent(sourceNodes, currentNode)) return null;
      currentNode = currentNode.getParent();
      if (currentNode == null) return null;
    }
  }

  private TreeNode[] removeRedundantSourceNodes(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode, final int dropAction) {
    final DropHandler dropHandler = getDropHandler(dropAction);
    List<TreeNode> result = new ArrayList<TreeNode>(sourceNodes.length);
    for (TreeNode sourceNode : sourceNodes) {
      if (!dropHandler.isDropRedundant(sourceNode, targetNode)) {
        result.add(sourceNode);
      }
    }
    return result.toArray(new TreeNode[result.size()]);
  }

  public DropHandler getDropHandler(final int dropAction) {
    return (dropAction == DnDConstants.ACTION_COPY ) ? new CopyDropHandler() : new MoveDropHandler();
  }

  private interface DropHandler {
    boolean isValidSource(@NotNull TreeNode[] sourceNodes);

    boolean isValidTarget(@NotNull TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    boolean shouldDelegateToParent(TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode);

    void doDrop(@NotNull TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    void doDropFiles(List<File> fileList, TreeNode targetNode);
  }

  public abstract class MoveCopyDropHandler implements DropHandler {

    public boolean isValidSource(@NotNull final TreeNode[] sourceNodes) {
      return canDrop(sourceNodes, null);
    }

    public boolean isValidTarget(@NotNull final TreeNode[] sourceNodes, final @NotNull TreeNode targetNode) {
      return canDrop(sourceNodes, targetNode);
    }

    protected abstract boolean canDrop(@NotNull TreeNode[] sourceNodes, @Nullable TreeNode targetNode);

    @Nullable
    protected PsiElement getPsiElement(@Nullable final TreeNode treeNode) {
      return myPsiRetriever.getPsiElement(treeNode);
    }

    @NotNull protected PsiElement[] getPsiElements(@NotNull TreeNode[] nodes) {
      List<PsiElement> psiElements = new ArrayList<PsiElement>(nodes.length);
      for (TreeNode node : nodes) {
        PsiElement psiElement = getPsiElement(node);
        if (psiElement != null) {
          psiElements.add(psiElement);
        }
      }
      if ( psiElements.size() != 0) {
        return psiElements.toArray(new PsiElement[psiElements.size()]);
      } else {
        return BaseRefactoringAction.getPsiElementArray(DataManager.getInstance().getDataContext(myTree));
      }
    }

    protected boolean fromSameProject(final PsiElement[] sourceElements, final PsiElement targetElement) {
      return targetElement != null && sourceElements.length > 0 && sourceElements[0] != null &&
             targetElement.getProject() == sourceElements[0].getProject();
    }

    protected PsiFileSystemItem[] getPsiFiles(List<File> fileList) {
      List<PsiFileSystemItem> sourceFiles = new ArrayList<PsiFileSystemItem>();
      for (File file : fileList) {
        final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (vFile != null) {
          final PsiFileSystemItem psiFile = vFile.isDirectory()
                                            ? PsiManager.getInstance(myProject).findDirectory(vFile)
                                            : PsiManager.getInstance(myProject).findFile(vFile);
          if (psiFile != null) {
            sourceFiles.add(psiFile);
          }
        }
      }
      return sourceFiles.toArray(new PsiFileSystemItem[sourceFiles.size()]);
    }
  }

  private class MoveDropHandler extends MoveCopyDropHandler {

    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      if (targetNode instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)targetNode).getUserObject();
        if (userObject instanceof DropTargetNode && ((DropTargetNode) userObject).canDrop(sourceNodes)) {
          return true;
        }
      }
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return sourceElements.length == 0 ||
             ((targetNode == null || targetElement != null) &&
              fromSameProject(sourceElements, targetElement) && MoveHandler.canMove(sourceElements, targetElement));
    }

    public void doDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      if (targetNode instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)targetNode).getUserObject();
        if (userObject instanceof DropTargetNode && ((DropTargetNode) userObject).canDrop(sourceNodes)) {
          ((DropTargetNode) userObject).drop(sourceNodes);
        }
      }
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      doDrop(targetNode, sourceElements);
    }

    private void doDrop(TreeNode targetNode, PsiElement[] sourceElements) {
      final PsiElement targetElement = getPsiElement(targetNode);
      if (targetElement == null) return;
      final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      getActionHandler(dataContext).invoke(myProject, sourceElements, new DataContext() {
        @Nullable
        public Object getData(@NonNls String dataId) {
          if (LangDataKeys.TARGET_PSI_ELEMENT.is(dataId)) {
            return targetElement;
          }
          else {
            return dataContext.getData(dataId);
          }
        }
      });
    }

    private RefactoringActionHandler getActionHandler(final DataContext dataContext) {
      return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
    }

    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return sourceNode.getParent() == targetNode || MoveHandler.isMoveRedundant(getPsiElement(sourceNode), getPsiElement(targetNode));
    }

    public boolean shouldDelegateToParent(TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement psiElement = getPsiElement(targetNode);
      return !MoveHandler.isValidTarget(psiElement, getPsiElements(sourceNodes));
    }

    public void doDropFiles(List<File> fileList, TreeNode targetNode) {
      final PsiFileSystemItem[] sourceFileArray = getPsiFiles(fileList);
      doDrop(targetNode, sourceFileArray);
    }
  }

  private class CopyDropHandler extends MoveCopyDropHandler {

    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return (targetElement instanceof PsiDirectoryContainer || targetElement instanceof PsiDirectory) &&
             fromSameProject(sourceElements, targetElement) && CopyHandler.canCopy(sourceElements);
    }

    public void doDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      doDrop(targetNode, sourceElements);
    }

    private void doDrop(TreeNode targetNode, PsiElement[] sourceElements) {
      final PsiElement targetElement = getPsiElement(targetNode);
      final PsiDirectory psiDirectory;
      if (targetElement instanceof PsiDirectoryContainer) {
        final PsiDirectoryContainer directoryContainer = (PsiDirectoryContainer)targetElement;
        final PsiDirectory[] psiDirectories = directoryContainer.getDirectories();
        psiDirectory = psiDirectories.length != 0 ? psiDirectories[0] : null;
      } else {
        psiDirectory = (PsiDirectory)targetElement;
      }
      CopyHandler.doCopy(sourceElements, psiDirectory);
    }

    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return false;
    }

    public boolean shouldDelegateToParent(TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement psiElement = getPsiElement(targetNode);
      return psiElement == null || (!(psiElement instanceof PsiDirectoryContainer) && !(psiElement instanceof PsiDirectory));
    }

    public void doDropFiles(List<File> fileList, TreeNode targetNode) {
      final PsiFileSystemItem[] sourceFileArray = getPsiFiles(fileList);
      doDrop(targetNode, sourceFileArray);
    }
  }
}
