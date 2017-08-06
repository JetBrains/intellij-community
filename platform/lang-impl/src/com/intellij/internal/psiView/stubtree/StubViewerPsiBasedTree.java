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
package com.intellij.internal.psiView.stubtree;

import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.internal.psiView.ViewerPsiBasedTree;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.IOException;

import static com.intellij.internal.psiView.PsiViewerDialog.initTree;

public class StubViewerPsiBasedTree implements ViewerPsiBasedTree {

  private static final Key<Object> PSI_ELEMENT_SELECTION_REQUESTOR = Key.create("SelectionRequester");
  ;

  @Nullable
  private PsiViewerStubTreeBuilder myStubTreeBuilder;
  @NotNull
  private final Tree myStubTree;
  @Nullable
  private JPanel myPanel;
  @NotNull
  private final Project myProject;
  @NotNull
  private final PsiTreeUpdater myUpdater;


  public StubViewerPsiBasedTree(@NotNull Project project, @NotNull PsiTreeUpdater updater) {
    myProject = project;
    myUpdater = updater;
    myStubTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }

  @Override
  public void reloadTree(PsiElement rootRootElement, @NotNull String text) {
    if (myStubTreeBuilder != null) {
      Disposer.dispose(myStubTreeBuilder);
      myStubTreeBuilder = null;
    }
    
    buildStubTree(rootRootElement, text);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    if (myPanel != null) return myPanel;
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myStubTree));
    panel.setBorder(IdeBorderFactory.createBorder());
    initTree(myStubTree);
    myPanel = panel;
    return panel;
  }

  @Override
  public boolean isFocusOwner() {
    return myStubTree.isFocusOwner();
  }

  @Override
  public void focusTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myStubTree, true);
  }

  private void buildStubTree(PsiElement rootElement, @NotNull String textToParse) {
    if (!(rootElement instanceof PsiFileWithStubSupport)) {
      myStubTree.setRootVisible(false);
      myStubTree.removeAll();
      StatusText text = myStubTree.getEmptyText();
      if (rootElement instanceof PsiFile) {
        text.setText("No stubs for " + rootElement.getLanguage().getDisplayName());
      }
      else {
        text.setText("Cannot build stub tree for code fragments");
      }
      return;
    }
    Stub stub = buildStubForElement(myProject, rootElement, textToParse);

    if (stub instanceof StubElement) {
      final StubTreeNode rootNode = new StubTreeNode((StubElement)stub, null);
      final StubTreeStructure treeStructure = new StubTreeStructure(rootNode);
      myStubTreeBuilder = new PsiViewerStubTreeBuilder(myStubTree, treeStructure);
      myStubTree.setRootVisible(true);
      myStubTree.expandRow(0);
      myStubTreeBuilder.queueUpdate();
    }
    else {
      myStubTree.setRootVisible(false);
      myStubTree.removeAll();
      StatusText text = myStubTree.getEmptyText();
      text.setText("No stubs for " + rootElement.getLanguage().getDisplayName());
    }
  }

  @Override
  public void dispose() {
    myStubTree.removeAll();
    if (myStubTreeBuilder != null) {
      Disposer.dispose(myStubTreeBuilder);
    }
  }

  @Nullable
  private static Stub buildStubForElement(Project project, PsiElement rootElement, @NotNull String textToParse) {
    Stub stub = null;
    StubTree tree = ((PsiFileWithStubSupport)rootElement).getStubTree();
    if (tree != null) {
      stub = tree.getRoot();
    }
    else if (rootElement instanceof PsiFileImpl) {
      IStubFileElementType builder = ((PsiFileImpl)rootElement).getElementTypeForStubBuilder();
      stub = builder == null ? null : builder.getBuilder().buildStubTree((PsiFile)rootElement);
    }
    if (stub == null) {
      LightVirtualFile file = new LightVirtualFile("stub", rootElement.getLanguage(), textToParse);
      final FileContentImpl fc;
      try {
        fc = new FileContentImpl(file, file.contentsToByteArray());
        fc.putUserData(IndexingDataKeys.PROJECT, project);
        stub = StubTreeBuilder.buildStubTree(fc);
      }
      catch (IOException e) {

      }
    }
    return stub;
  }

  public void selectNodeForPsi(@Nullable PsiElement element) {
    if (myStubTreeBuilder == null || element == null) return;
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof PsiFileWithStubSupport)) return;

    final PsiFileWithStubSupport stubFile = (PsiFileWithStubSupport)file;
    final StubTreeNode rootNode = (StubTreeNode)myStubTreeBuilder.getRootElement();
    if (rootNode == null) return;

    final StubElement<?> stub = rootNode.getStub();
    if (!(stub instanceof PsiFileStub)) return;

    final StubTree stubTree = new StubTree((PsiFileStub)stub);
    final TextRange elementTextRange = element.getTextRange();

    element.putUserData(PSI_ELEMENT_SELECTION_REQUESTOR, true);
    myStubTreeBuilder.select(StubTreeNode.class, new TreeVisitor<StubTreeNode>() {
      @Override
      public boolean visit(@NotNull StubTreeNode node) {
        final ASTNode stub = stubFile.findTreeForStub(stubTree, node.getStub());
        return stub != null && stub.getTextRange().equals(elementTextRange);
      }
    }, null, false);

    element.putUserData(PSI_ELEMENT_SELECTION_REQUESTOR, false);
  }
}
