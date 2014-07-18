/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class DefaultStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.DefaultStubBuilder");

  @Override
  public StubElement buildStubTree(@NotNull PsiFile file) {
    return buildStubTreeFor(file, createStubForFile(file));
  }

  @NotNull
  protected StubElement createStubForFile(@NotNull PsiFile file) {
    @SuppressWarnings("unchecked") PsiFileStubImpl stub = new PsiFileStubImpl(file);
    return stub;
  }

  @NotNull
  private StubElement buildStubTreeFor(@NotNull PsiElement root, @NotNull StubElement parentStub) {
    Stack<StubElement> parentStubs = new Stack<StubElement>();
    Stack<PsiElement> parentElements = new Stack<PsiElement>();
    parentElements.push(root);
    parentStubs.push(parentStub);

    while (!parentElements.isEmpty()) {
      StubElement stub = parentStubs.pop();
      PsiElement elt = parentElements.pop();

      if (elt instanceof StubBasedPsiElement) {
        final IStubElementType type = ((StubBasedPsiElement)elt).getElementType();

        if (type.shouldCreateStub(elt.getNode())) {
          @SuppressWarnings("unchecked") StubElement s = type.createStub(elt, stub);
          stub = s;
        }
      }
      else {
        final ASTNode node = elt.getNode();
        final IElementType type = node == null? null : node.getElementType();
        if (type instanceof IStubElementType && ((IStubElementType)type).shouldCreateStub(node)) {
          LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + elt);
        }
      }

      for (PsiElement child = elt.getLastChild(); child != null; child = child.getPrevSibling()) {
        if (!skipChildProcessingWhenBuildingStubs(elt, child)) {
          parentStubs.push(stub);
          parentElements.push(child);
        }
      }
    }
    return parentStub;
  }

  /**
   * Note to implementers: always keep in sync with {@linkplain #skipChildProcessingWhenBuildingStubs(ASTNode, ASTNode)}.
   */
  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull PsiElement parent, @NotNull PsiElement element) {
    return false;
  }

  @NotNull
  protected StubElement buildStubTreeFor(@NotNull ASTNode root, @NotNull StubElement parentStub) {
    Stack<StubElement> parentStubs = new Stack<StubElement>();
    Stack<ASTNode> parentNodes = new Stack<ASTNode>();
    parentNodes.push(root);
    parentStubs.push(parentStub);

    while (!parentStubs.isEmpty()) {
      StubElement stub = parentStubs.pop();
      ASTNode node = parentNodes.pop();
      IElementType nodeType = node.getElementType();

      if (nodeType instanceof IStubElementType) {
        final IStubElementType type = (IStubElementType)nodeType;

        if (type.shouldCreateStub(node)) {
          PsiElement element = node.getPsi();
          if (!(element instanceof StubBasedPsiElement)) {
            LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + element);
          }
          @SuppressWarnings("unchecked") StubElement s = type.createStub(element, stub);
          stub = s;
          LOG.assertTrue(stub != null, element);
        }
      }

      for (ASTNode childNode = node.getLastChildNode(); childNode != null; childNode = childNode.getTreePrev()) {
        if (!skipChildProcessingWhenBuildingStubs(node, childNode)) {
          parentNodes.push(childNode);
          parentStubs.push(stub);
        }
      }
    }

    return parentStub;
  }

  /**
   * Note to implementers: always keep in sync with {@linkplain #skipChildProcessingWhenBuildingStubs(PsiElement, PsiElement)}.
   */
  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    return false;
  }
}
