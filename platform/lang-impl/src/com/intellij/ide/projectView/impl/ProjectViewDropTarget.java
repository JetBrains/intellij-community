/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.projectView.impl.nodes.DropTargetNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anna
 * @author Konstantin Bulenkov
 */
class ProjectViewDropTarget implements DnDNativeTarget {

  private final JTree myTree;
  private final Retriever myRetriever;
  private final Project myProject;

  ProjectViewDropTarget(JTree tree, Retriever retriever, Project project) {
    myTree = tree;
    myRetriever = retriever;
    myProject = project;
  }

  @Override
  public boolean update(DnDEvent event) {
    event.setDropPossible(false, "");

    final Object attached = event.getAttachedObject();
    final int dropAction = event.getAction().getActionId();
    final DropHandler dropHandler = getDropHandler(dropAction);
    final TreeNode[] sourceNodes = getSourceNodes(attached);
    final Point point = event.getPoint();
    final TreeNode targetNode = getTargetNode(point);

    if (targetNode == null ||
        (dropAction & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ||
        sourceNodes == null && !FileCopyPasteUtil.isFileListFlavorAvailable(event) ||
        sourceNodes != null && ArrayUtilRt.find(sourceNodes, targetNode) != -1 ||
        sourceNodes != null && !dropHandler.isValidSource(sourceNodes, targetNode)) {
      return false;
    }

    if (sourceNodes != null) {
      boolean redundant = true;
      for (TreeNode sourceNode : sourceNodes) {
        if (!dropHandler.isDropRedundant(sourceNode, targetNode)) {
          redundant = false;
          break;
        }
      }
      if (redundant) return false;
    }
    else {
      // it seems like it's not possible to obtain dragged items _before_ accepting _drop_ on Macs, so just skip this check
      if (!SystemInfo.isMac) {
        final PsiFileSystemItem[] psiFiles = getPsiFiles(FileCopyPasteUtil.getFileListFromAttachedObject(attached));
        if (psiFiles == null || psiFiles.length == 0) return false;
        if (!MoveHandler.isValidTarget(getPsiElement(targetNode), psiFiles)) return false;
      }
    }

    final Rectangle pathBounds = myTree.getPathBounds(myTree.getClosestPathForLocation(point.x, point.y));
    if (pathBounds != null && pathBounds.y + pathBounds.height < point.y) return false;
    event.setHighlighting(new RelativeRectangle(myTree, pathBounds), DnDEvent.DropTargetHighlightingType.RECTANGLE);
    event.setDropPossible(true);
    return false;
  }

  @Override
  public void drop(DnDEvent event) {
    final Object attached = event.getAttachedObject();
    final TreeNode[] sourceNodes = getSourceNodes(attached);
    final TreeNode targetNode = getTargetNode(event.getPoint());
    assert targetNode != null;
    final int dropAction = event.getAction().getActionId();
    if (sourceNodes == null) {
      if (FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
        List<File> fileList = FileCopyPasteUtil.getFileListFromAttachedObject(attached);
        if (!fileList.isEmpty()) {
          getDropHandler(dropAction).doDropFiles(fileList, targetNode);
        }
      }
    }
    else {
      doDrop(sourceNodes, targetNode, dropAction);
    }
  }

  @Override
  public void cleanUpOnLeave() {
  }

  @Override
  public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
  }

  @Nullable
  private static TreeNode[] getSourceNodes(final Object transferData) {
    if (transferData instanceof TransferableWrapper) {
      return ((TransferableWrapper)transferData).getTreeNodes();
    }
    return null;
  }

  @Nullable
  private TreeNode getTargetNode(final Point location) {
    final TreePath path = myTree.getClosestPathForLocation(location.x, location.y);
    return path == null ? null : (TreeNode)path.getLastPathComponent();
  }

  private boolean doDrop(@NotNull final TreeNode[] sourceNodes,
                         @NotNull final TreeNode targetNode,
                         final int dropAction) {
    TreeNode validTargetNode = getValidTargetNode(sourceNodes, targetNode, dropAction);
    if (validTargetNode != null) {
      final TreeNode[] filteredSourceNodes = removeRedundantSourceNodes(sourceNodes, validTargetNode, dropAction);
      if (filteredSourceNodes.length != 0) {
        getDropHandler(dropAction).doDrop(filteredSourceNodes, validTargetNode);
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

  private TreeNode[] removeRedundantSourceNodes(@NotNull final TreeNode[] sourceNodes,
                                                @NotNull final TreeNode targetNode,
                                                final int dropAction) {
    final DropHandler dropHandler = getDropHandler(dropAction);
    List<TreeNode> result = new ArrayList<>(sourceNodes.length);
    for (TreeNode sourceNode : sourceNodes) {
      if (!dropHandler.isDropRedundant(sourceNode, targetNode)) {
        result.add(sourceNode);
      }
    }
    return result.toArray(new TreeNode[result.size()]);
  }

  public DropHandler getDropHandler(final int dropAction) {
    return (dropAction == DnDConstants.ACTION_COPY) ? new CopyDropHandler() : new MoveDropHandler();
  }

  private interface DropHandler {
    boolean isValidSource(@NotNull TreeNode[] sourceNodes, TreeNode targetNode);

    boolean isValidTarget(@NotNull TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    boolean shouldDelegateToParent(TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode);

    void doDrop(@NotNull TreeNode[] sourceNodes, @NotNull TreeNode targetNode);

    void doDropFiles(List<File> fileList, TreeNode targetNode);
  }

  @Nullable
  protected PsiElement getPsiElement(@Nullable final TreeNode treeNode) {
    return myRetriever.getPsiElement(treeNode);
  }

  protected Module getModule(@Nullable final TreeNode treeNode) {
    return myRetriever.getModule(treeNode);
  }

  public abstract class MoveCopyDropHandler implements DropHandler {
    @Override
    public boolean isValidSource(@NotNull final TreeNode[] sourceNodes, TreeNode targetNode) {
      return canDrop(sourceNodes, targetNode);
    }

    @Override
    public boolean isValidTarget(@NotNull final TreeNode[] sourceNodes, final @NotNull TreeNode targetNode) {
      return canDrop(sourceNodes, targetNode);
    }

    protected abstract boolean canDrop(@NotNull TreeNode[] sourceNodes, @Nullable TreeNode targetNode);

    @NotNull
    protected PsiElement[] getPsiElements(@NotNull TreeNode[] nodes) {
      List<PsiElement> psiElements = new ArrayList<>(nodes.length);
      for (TreeNode node : nodes) {
        PsiElement psiElement = getPsiElement(node);
        if (psiElement != null) {
          psiElements.add(psiElement);
        }
      }
      if (psiElements.size() != 0) {
        return PsiUtilCore.toPsiElementArray(psiElements);
      }
      else {
        return BaseRefactoringAction.getPsiElementArray(DataManager.getInstance().getDataContext(myTree));
      }
    }
  }

  @Nullable
  protected PsiFileSystemItem[] getPsiFiles(@Nullable List<File> fileList) {
    if (fileList == null) return null;
    List<PsiFileSystemItem> sourceFiles = new ArrayList<>();
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

  private class MoveDropHandler extends MoveCopyDropHandler {
    @Override
    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      if (targetNode instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)targetNode).getUserObject();
        if (userObject instanceof DropTargetNode && ((DropTargetNode)userObject).canDrop(sourceNodes)) {
          return true;
        }
      }
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      return sourceElements.length == 0 ||
             ((targetNode == null || targetElement != null) &&
              MoveHandler.canMove(sourceElements, targetElement));
    }

    @Override
    public void doDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      if (targetNode instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)targetNode).getUserObject();
        if (userObject instanceof DropTargetNode && ((DropTargetNode)userObject).canDrop(sourceNodes)) {
          final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
          ((DropTargetNode)userObject).drop(sourceNodes, dataContext);
        }
      }
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      doDrop(targetNode, sourceElements, false);
    }

    private void doDrop(TreeNode targetNode, PsiElement[] sourceElements, final boolean externalDrop) {
      final PsiElement targetElement = getPsiElement(targetNode);
      if (targetElement == null) return;

      if (DumbService.isDumb(myProject)) {
        Messages.showMessageDialog(myProject, "Move refactoring is not available while indexing is in progress", "Indexing", null);
        return;
      }
      
      final Module module = getModule(targetNode);
      final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      if (!targetElement.isValid()) return;
      for (PsiElement sourceElement : sourceElements) {
        if (!sourceElement.isValid()) return;
      }

      getActionHandler().invoke(myProject, sourceElements, new DataContext() {
        @Override
        @Nullable
        public Object getData(@NonNls String dataId) {
          if (LangDataKeys.TARGET_MODULE.is(dataId)) {
            if (module != null) return module;
          }
          if (LangDataKeys.TARGET_PSI_ELEMENT.is(dataId)) {
            return targetElement;
          }
          else {
            return externalDrop ? null : dataContext.getData(dataId);
          }
        }
      });
    }

    private RefactoringActionHandler getActionHandler() {
      return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
    }

    @Override
    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return sourceNode.getParent() == targetNode || MoveHandler.isMoveRedundant(getPsiElement(sourceNode), getPsiElement(targetNode));
    }

    @Override
    public boolean shouldDelegateToParent(TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement psiElement = getPsiElement(targetNode);
      return !MoveHandler.isValidTarget(psiElement, getPsiElements(sourceNodes));
    }

    @Override
    public void doDropFiles(List<File> fileList, TreeNode targetNode) {
      final PsiFileSystemItem[] sourceFileArray = getPsiFiles(fileList);

      if (targetNode instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)targetNode).getUserObject();
        if (userObject instanceof DropTargetNode) {
          final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
          ((DropTargetNode)userObject).dropExternalFiles(sourceFileArray, dataContext);
          return;
        }
      }
      doDrop(targetNode, sourceFileArray, true);
    }
  }

  private class CopyDropHandler extends MoveCopyDropHandler {
    @Override
    protected boolean canDrop(@NotNull final TreeNode[] sourceNodes, @Nullable final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      final PsiElement targetElement = getPsiElement(targetNode);
      if (targetElement == null) return false;
      final PsiFile containingFile = targetElement.getContainingFile();
      final boolean isTargetAcceptable = targetElement instanceof PsiDirectoryContainer ||
                                         targetElement instanceof PsiDirectory ||
                                         (containingFile != null && containingFile.getContainingDirectory() != null);
      return isTargetAcceptable && CopyHandler.canCopy(sourceElements);
    }

    @Override
    public void doDrop(@NotNull final TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement[] sourceElements = getPsiElements(sourceNodes);
      doDrop(targetNode, sourceElements);
    }

    private void doDrop(TreeNode targetNode, PsiElement[] sourceElements) {
      final PsiElement targetElement = getPsiElement(targetNode);
      if (targetElement == null) return;
      final PsiDirectory psiDirectory;
      if (targetElement instanceof PsiDirectoryContainer) {
        final PsiDirectoryContainer directoryContainer = (PsiDirectoryContainer)targetElement;
        final PsiDirectory[] psiDirectories = directoryContainer.getDirectories();
        psiDirectory = psiDirectories.length != 0 ? psiDirectories[0] : null;
      }
      else if (targetElement instanceof PsiDirectory) {
        psiDirectory = (PsiDirectory)targetElement;
      }
      else {
        final PsiFile containingFile = targetElement.getContainingFile();
        LOG.assertTrue(containingFile != null);
        psiDirectory = containingFile.getContainingDirectory();
      }
      CopyHandler.doCopy(sourceElements, psiDirectory);
    }

    @Override
    public boolean isDropRedundant(@NotNull TreeNode sourceNode, @NotNull TreeNode targetNode) {
      return false;
    }

    @Override
    public boolean shouldDelegateToParent(TreeNode[] sourceNodes, @NotNull final TreeNode targetNode) {
      final PsiElement psiElement = getPsiElement(targetNode);
      return psiElement == null || (!(psiElement instanceof PsiDirectoryContainer) && !(psiElement instanceof PsiDirectory));
    }

    @Override
    public void doDropFiles(List<File> fileList, TreeNode targetNode) {
      final PsiFileSystemItem[] sourceFileArray = getPsiFiles(fileList);
      doDrop(targetNode, sourceFileArray);
    }
  }
}
