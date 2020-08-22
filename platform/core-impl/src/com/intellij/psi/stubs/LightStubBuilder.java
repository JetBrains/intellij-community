// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.lang.*;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.containers.BooleanStack;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LightStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance(LightStubBuilder.class);
  public static final ThreadLocal<LighterAST> FORCED_AST = new ThreadLocal<>();

  @Override
  public StubElement buildStubTree(@NotNull PsiFile file) {
    LighterAST tree = FORCED_AST.get();
    if (tree == null) {
      FileType fileType = file.getFileType();
      if (!(fileType instanceof LanguageFileType)) {
        LOG.error("File is not of LanguageFileType: " + file + ", " + fileType);
        return null;
      }
      if (!(file instanceof PsiFileImpl)) {
        LOG.error("Unexpected PsiFile instance: " + file + ", " + file.getClass());
        return null;
      }
      if (((PsiFileImpl)file).getElementTypeForStubBuilder() == null) {
        LOG.error("File is not of IStubFileElementType: " + file);
        return null;
      }

      FileASTNode node = file.getNode();
      tree = node.getElementType() instanceof ILightStubFileElementType ? node.getLighterAST() : new TreeBackedLighterAST(node);
    }
    else {
      FORCED_AST.set(null);
    }

    StubElement rootStub = createStubForFile(file, tree);
    buildStubTree(tree, tree.getRoot(), rootStub);
    return rootStub;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected StubElement createStubForFile(@NotNull PsiFile file, @NotNull LighterAST tree) {
    return new PsiFileStubImpl(file);
  }

  protected void buildStubTree(@NotNull LighterAST tree, @NotNull LighterASTNode root, @NotNull StubElement rootStub) {
    final Stack<LighterASTNode> parents = new Stack<>();
    final IntStack childNumbers = new IntArrayList();
    final BooleanStack parentsStubbed = new BooleanStack();
    final Stack<List<LighterASTNode>> kinderGarden = new Stack<>();
    final Stack<StubElement> parentStubs = new Stack<>();

    LighterASTNode parent = null;
    LighterASTNode element = root;
    List<LighterASTNode> children = null;
    int childNumber = 0;
    StubElement parentStub = rootStub;
    boolean immediateParentStubbed = true;

    nextElement:
    while (element != null) {
      ProgressManager.checkCanceled();

      final StubElement stub = createStub(tree, element, parentStub);
      boolean hasStub = stub != parentStub || parent == null;
      if (hasStub && !immediateParentStubbed) {
        ((ObjectStubBase) stub).markDangling();
      }

      if (parent == null || !skipNode(tree, parent, element)) {
        final List<LighterASTNode> kids = tree.getChildren(element);
        if (!kids.isEmpty()) {
          if (parent != null) {
            parents.push(parent);
            childNumbers.push(childNumber);
            kinderGarden.push(children);
            parentStubs.push(parentStub);
            parentsStubbed.push(immediateParentStubbed);
          }
          parent = element;
          immediateParentStubbed = hasStub;
          element = (children = kids).get(childNumber = 0);
          parentStub = stub;
          if (!skipNode(tree, parent, element)) continue;
        }
      }

      while (children != null && ++childNumber < children.size()) {
        element = children.get(childNumber);
        if (!skipNode(tree, parent, element)) continue nextElement;
      }

      element = null;
      while (!parents.isEmpty()) {
        parent = parents.pop();
        childNumber = childNumbers.popInt();
        children = kinderGarden.pop();
        parentStub = parentStubs.pop();
        immediateParentStubbed = parentsStubbed.pop();
        while (++childNumber < children.size()) {
          element = children.get(childNumber);
          if (!skipNode(tree, parent, element)) continue nextElement;
        }
        element = null;
      }
    }
  }

  @NotNull
  private static StubElement createStub(@NotNull LighterAST tree, @NotNull LighterASTNode element, @NotNull StubElement parentStub) {
    final IElementType elementType = element.getTokenType();
    if (elementType instanceof IStubElementType) {
      if (elementType instanceof ILightStubElementType) {
        final ILightStubElementType lightElementType = (ILightStubElementType)elementType;
        if (lightElementType.shouldCreateStub(tree, element, parentStub)) {
          return lightElementType.createStub(tree, element, parentStub);
        }
      }
      else {
        LOG.error("Element is not of ILightStubElementType: " + LogUtil.objectAndClass(elementType) + ", " + element);
      }
    }

    return parentStub;
  }

  private boolean skipNode(@NotNull LighterAST tree, @NotNull LighterASTNode parent, @NotNull LighterASTNode node) {
    if (tree instanceof TreeBackedLighterAST) {
      return skipChildProcessingWhenBuildingStubs(((TreeBackedLighterAST)tree).unwrap(parent), ((TreeBackedLighterAST)tree).unwrap(node));
    }
    else {
      return skipChildProcessingWhenBuildingStubs(tree, parent, node);
    }
  }

  /**
   * Note to implementers: always keep in sync with {@linkplain #skipChildProcessingWhenBuildingStubs(LighterAST, LighterASTNode, LighterASTNode)}.
   */
  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    return false;
  }

  /**
   * Note to implementers: always keep in sync with {@linkplain #skipChildProcessingWhenBuildingStubs(ASTNode, ASTNode)}.
   */
  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull LighterAST tree, @NotNull LighterASTNode parent, @NotNull LighterASTNode node) {
    return false;
  }
}