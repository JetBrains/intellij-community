// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.BooleanStack;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance(DefaultStubBuilder.class);

  @Override
  public StubElement buildStubTree(@NotNull PsiFile file) {
    return buildStubTreeFor(file.getNode(), createStubForFile(file));
  }

  @NotNull
  protected StubElement createStubForFile(@NotNull PsiFile file) {
    @SuppressWarnings("unchecked") PsiFileStubImpl stub = new PsiFileStubImpl(file);
    return stub;
  }

  @NotNull
  protected final StubElement buildStubTreeFor(@NotNull ASTNode root, @NotNull StubElement parentStub) {
    new StubBuildingWalkingVisitor(root, parentStub).buildStubTree();
    return parentStub;
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    return false;
  }

  protected class StubBuildingWalkingVisitor {
    private final Stack<StubElement> parentStubs = new Stack<>();
    private final Stack<ASTNode> parentNodes = new Stack<>();
    private final BooleanStack parentNodesStubbed = new BooleanStack();

    protected StubBuildingWalkingVisitor(ASTNode root, StubElement parentStub) {
      parentNodes.push(root);
      parentStubs.push(parentStub);
      parentNodesStubbed.push(true);
    }

    public final void buildStubTree() {
      while (!parentStubs.isEmpty()) {
        visitNode(parentStubs.pop(), parentNodes.pop(), parentNodesStubbed.pop());
      }
    }

    protected void visitNode(StubElement parentStub, ASTNode node, boolean immediateParentStubbed) {
      StubElement stub = createStub(parentStub, node);
      if (stub != null && !immediateParentStubbed) {
        ((ObjectStubBase<?>)stub).markDangling();
      }

      pushChildren(node, node instanceof FileElement || stub != null, stub != null ? stub : parentStub);
    }

    @Nullable
    protected final ASTNode peekNextElement() {
      return parentNodes.isEmpty() ? null : parentNodes.peek();
    }

    @Nullable
    protected StubElement createStub(StubElement parentStub, ASTNode node) {
      IElementType nodeType = node.getElementType();

      if (nodeType instanceof IStubElementType) {
        IStubElementType type = (IStubElementType)nodeType;

        if (type.shouldCreateStub(node)) {
          PsiElement element = node.getPsi();
          if (!(element instanceof StubBasedPsiElement)) {
            LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + element + ", language: #" + type.getLanguage());
          }
          @SuppressWarnings("unchecked") StubElement stub = type.createStub(element, parentStub);
          //noinspection ConstantConditions
          LOG.assertTrue(stub != null, element);
          return stub;
        }
      }
      return null;
    }

    private void pushChildren(ASTNode node, boolean hasStub, StubElement stub) {
      for (ASTNode childNode = node.getLastChildNode(); childNode != null; childNode = childNode.getTreePrev()) {
        if (!skipChildProcessingWhenBuildingStubs(node, childNode)) {
          parentNodes.push(childNode);
          parentStubs.push(stub);
          parentNodesStubbed.push(hasStub);
        }
      }
    }
  }
}