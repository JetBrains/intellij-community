/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.psiView.formattingblocks;

import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.internal.psiView.PsiViewerDialog;
import com.intellij.internal.psiView.ViewerPsiBasedTree;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Set;

import static com.intellij.internal.psiView.PsiViewerDialog.initTree;

public class BlockViewerPsiBasedTree implements ViewerPsiBasedTree {
  
  @NotNull
  private final JPanel myBlockStructurePanel;
  @Nullable
  private BlockTreeBuilder myBlockTreeBuilder;
  @NotNull
  private final Tree myBlockTree;
  @NotNull
  private final Project myProject;
  @NotNull
  private final PsiTreeUpdater myUpdater;

  private int myIgnoreBlockTreeSelectionMarker = 0;
  @Nullable
  private HashMap<PsiElement, BlockTreeNode> myPsiToBlockMap;

  public BlockViewerPsiBasedTree(@NotNull Project project, @NotNull PsiTreeUpdater updater) {
    myProject = project;
    myUpdater = updater;
    myBlockTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myBlockStructurePanel = new JPanel(new BorderLayout());
    myBlockStructurePanel.add(ScrollPaneFactory.createScrollPane(myBlockTree));
    myBlockStructurePanel.setBorder(IdeBorderFactory.createBorder());
    initTree(myBlockTree);
  }

  @Override
  public void reloadTree(@Nullable PsiElement rootRootElement, @NotNull String text) {
    if (myBlockTreeBuilder != null) {
      Disposer.dispose(myBlockTreeBuilder);
      myBlockTreeBuilder = null;
    }

    buildBlockTree(rootRootElement);
  }

  @Override
  public void selectNodeFromPsi(@Nullable PsiElement element) {
    if (myBlockTreeBuilder != null && element != null) {
      BlockTreeNode currentBlockNode = findBlockNode(element);
      if (currentBlockNode != null) {
        selectBlockNode(currentBlockNode);
      }
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myBlockStructurePanel;
  }

  @Override
  public boolean isFocusOwner() {
    return myBlockTree.isFocusOwner();
  }

  @Override
  public void focusTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myBlockTree, true);
  }

  @Override
  public void dispose() {
    myBlockTree.removeAll();
    if (myBlockTreeBuilder != null) {
      Disposer.dispose(myBlockTreeBuilder);
      myBlockTreeBuilder = null;
    }
  }

  private void buildBlockTree(PsiElement rootElement) {
    Block rootBlock = rootElement == null ? null : buildBlocks(rootElement);
    if (rootBlock == null) {
      myBlockTreeBuilder = null;
      myBlockTree.setRootVisible(false);
      myBlockTree.setVisible(false);
      return;
    }

    myBlockTree.setVisible(true);
    BlockTreeStructure blockTreeStructure = new BlockTreeStructure();
    BlockTreeNode rootNode = new BlockTreeNode(rootBlock, null);
    blockTreeStructure.setRoot(rootNode);
    myBlockTreeBuilder = new BlockTreeBuilder(myBlockTree, blockTreeStructure);
    initMap(rootNode, rootElement);
    assert myPsiToBlockMap != null;

    PsiElement rootPsi = rootNode.getBlock() instanceof ASTBlock ?
                         ((ASTBlock)rootNode.getBlock()).getNode().getPsi() : rootElement;
    BlockTreeNode blockNode = myPsiToBlockMap.get(rootPsi);

    if (blockNode == null) {
      PsiViewerDialog.LOG.error(LogMessageEx
                                  .createEvent("PsiViewer: rootNode not found",
                                               "Current language: " + rootElement.getContainingFile().getLanguage(),
                                               AttachmentFactory
                                                 .createAttachment(rootElement.getContainingFile().getOriginalFile().getVirtualFile())));
      blockNode = findBlockNode(rootPsi);
    }

    blockTreeStructure.setRoot(blockNode);
    myBlockTree.addTreeSelectionListener(new MyBlockTreeSelectionListener(rootElement));
    myBlockTree.setRootVisible(true);
    myBlockTree.expandRow(0);
    myBlockTreeBuilder.queueUpdate();
  }


  @Nullable
  private BlockTreeNode findBlockNode(PsiElement element) {
    HashMap<PsiElement, BlockTreeNode> psiToBlockMap = myPsiToBlockMap;

    BlockTreeNode result = psiToBlockMap == null ? null : psiToBlockMap.get(element);
    if (result == null) {
      TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
      result = findBlockNode(rangeInHostFile);
    }
    return result;
  }

  private void selectBlockNode(@Nullable BlockTreeNode currentBlockNode) {
    if (myBlockTreeBuilder == null) return;

    if (currentBlockNode != null) {
      myIgnoreBlockTreeSelectionMarker++;
      myBlockTreeBuilder.select(currentBlockNode, () -> {
        // hope this is always called!
        assert myIgnoreBlockTreeSelectionMarker > 0;
        myIgnoreBlockTreeSelectionMarker--;
      });
    }
    else {
      myIgnoreBlockTreeSelectionMarker++;
      try {
        myBlockTree.getSelectionModel().clearSelection();
      }
      finally {
        assert myIgnoreBlockTreeSelectionMarker > 0;
        myIgnoreBlockTreeSelectionMarker--;
      }
    }
  }

  public class MyBlockTreeSelectionListener implements TreeSelectionListener {
    private PsiElement myRootElement;

    public MyBlockTreeSelectionListener(PsiElement rootElement) {
      myRootElement = rootElement;
    }

    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
      if (myIgnoreBlockTreeSelectionMarker > 0 || myBlockTreeBuilder == null) {
        return;
      }

      Set<?> blockElementsSet = myBlockTreeBuilder.getSelectedElements();
      if (blockElementsSet.isEmpty()) return;
      BlockTreeNode descriptor = (BlockTreeNode)blockElementsSet.iterator().next();
      PsiElement rootPsi = myRootElement;
      int blockStart = descriptor.getBlock().getTextRange().getStartOffset();
      PsiFile file = rootPsi.getContainingFile();
      PsiElement currentPsiEl = InjectedLanguageUtil.findElementAtNoCommit(file, blockStart);
      if (currentPsiEl == null) currentPsiEl = file;
      int blockLength = descriptor.getBlock().getTextRange().getLength();
      while (currentPsiEl.getParent() != null &&
             currentPsiEl.getTextRange().getStartOffset() == blockStart &&
             currentPsiEl.getTextLength() != blockLength) {
        currentPsiEl = currentPsiEl.getParent();
      }
      final BlockTreeStructure treeStructure = ObjectUtils.notNull((BlockTreeStructure)myBlockTreeBuilder.getTreeStructure());
      BlockTreeNode rootBlockNode = treeStructure.getRootElement();
      int baseOffset = 0;
      if (rootBlockNode != null) {
        baseOffset = rootBlockNode.getBlock().getTextRange().getStartOffset();
      }

      TextRange range = descriptor.getBlock().getTextRange();
      range = range.shiftRight(-baseOffset);
      myUpdater.updatePsiTree(currentPsiEl, myBlockTree.hasFocus() ? range : null);
    }
  }

  @Nullable
  private BlockTreeNode findBlockNode(TextRange range) {
    final BlockTreeBuilder builder = myBlockTreeBuilder;
    if (builder == null || !myBlockStructurePanel.isVisible()) {
      return null;
    }

    AbstractTreeStructure treeStructure = builder.getTreeStructure();
    if (treeStructure == null) return null;
    BlockTreeNode node = (BlockTreeNode)treeStructure.getRootElement();
    main_loop:
    while (true) {
      if (node.getBlock().getTextRange().equals(range)) {
        return node;
      }

      for (BlockTreeNode child : node.getChildren()) {
        if (child.getBlock().getTextRange().contains(range)) {
          node = child;
          continue main_loop;
        }
      }
      return node;
    }
  }

  @Nullable
  private static Block buildBlocks(@NotNull PsiElement rootElement) {
    FormattingModelBuilder formattingModelBuilder = LanguageFormatting.INSTANCE.forContext(rootElement);
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(rootElement.getProject());
    if (formattingModelBuilder != null) {
      FormattingModel formattingModel = formattingModelBuilder.createModel(rootElement, settings);
      return formattingModel.getRootBlock();
    }
    else {
      return null;
    }
  }

  private void initMap(BlockTreeNode rootBlockNode, PsiElement psiEl) {
    myPsiToBlockMap = new HashMap<>();
    JBTreeTraverser<BlockTreeNode> traverser = new JBTreeTraverser<>(o -> JBIterable.of(o.getChildren()));
    for (BlockTreeNode block : traverser.withRoot(rootBlockNode)) {
      PsiElement currentElem = null;
      if (block.getBlock() instanceof ASTBlock) {
        ASTNode node = ((ASTBlock)block.getBlock()).getNode();
        if (node != null) {
          currentElem = node.getPsi();
        }
      }
      if (currentElem == null) {
        currentElem =
          InjectedLanguageUtil
            .findElementAtNoCommit(psiEl.getContainingFile(), block.getBlock().getTextRange().getStartOffset());
      }
      myPsiToBlockMap.put(currentElem, block);

      //nested PSI elements with same ranges will be mapped to one blockNode
      //    assert currentElem != null;      //for Scala-language plugin etc it can be null, because formatterBlocks is not instance of ASTBlock
      TextRange curTextRange = currentElem.getTextRange();
      PsiElement parentElem = currentElem.getParent();
      while (parentElem != null && parentElem.getTextRange().equals(curTextRange)) {
        myPsiToBlockMap.put(parentElem, block);
        parentElem = parentElem.getParent();
      }
    }
  }
}
