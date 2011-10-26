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
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class DefaultStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.DefaultStubBuilder");

  @Override
  public StubElement buildStubTree(@NotNull PsiFile file) {
    return buildStubTreeFor(file, createStubForFile(file));
  }

  protected StubElement createStubForFile(@NotNull PsiFile file) {
    //noinspection unchecked
    return new PsiFileStubImpl(file);
  }

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
          //noinspection unchecked
          stub = type.createStub(elt, stub);
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

  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull PsiElement element, @NotNull PsiElement child) {
    return false;
  }

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
          //noinspection unchecked
          PsiElement element = node.getPsi();
          if (!(element instanceof StubBasedPsiElement)) {
            LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + element);
          }
          //noinspection unchecked
          stub = type.createStub(element, stub);
          LOG.assertTrue(stub != null, element);
        }
      }

      for (ASTNode childNode = node.getLastChildNode(); childNode != null; childNode = childNode.getTreePrev()) {
        if (!skipChildProcessingWhenBuildingStubs(node, childNode.getElementType())) {
          parentNodes.push(childNode);
          parentStubs.push(stub);
        }
      }
    }

    return parentStub;
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@Nullable ASTNode parent, IElementType childType) {
    return false;
  }
}