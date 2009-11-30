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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class DefaultStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.DefaultStubBuilder");

  public StubElement buildStubTree(final PsiFile file) {
    StubElement stubForFile = createStubForFile(file);
    return file instanceof PsiCompiledElement ? buildStubTreeFor(file, stubForFile) : nonRecBuildStubTreeFor(file, stubForFile);
  }

  protected StubElement createStubForFile(final PsiFile file) {
    return new PsiFileStubImpl<PsiFile>(file);
  }

  private static StubElement buildStubTreeFor(PsiElement elt, StubElement parentStub) {
    StubElement stub = parentStub;
    if (elt instanceof StubBasedPsiElement) {
      final IStubElementType type = ((StubBasedPsiElement)elt).getElementType();

      if (type.shouldCreateStub(elt.getNode())) {
        //noinspection unchecked
        stub = type.createStub(elt, parentStub);
      }
    }
    else {
      final ASTNode node = elt.getNode();
      final IElementType type = node == null? null : node.getElementType();
      if (type instanceof IStubElementType && ((IStubElementType)type).shouldCreateStub(node)) {
        LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + elt);
      }
    }

    for (PsiElement child = elt.getFirstChild(); child != null; child = child.getNextSibling()) {
      buildStubTreeFor(child, stub);
    }

    return stub;
  }

  private static final Key<StubElement> PARENT_STUB = Key.create("PARENT_STUB");
  private static StubElement getParentStub(PsiElement element, PsiElement root, StubElement rootStub) {
    if (element == root) return rootStub;
    PsiElement parent = element.getParent();
    return parent.getUserData(PARENT_STUB);
  }
  private static void setParentStubForChildrenOf(PsiElement element, StubElement parentStub) {
    element.putUserData(PARENT_STUB, parentStub);
  }

  private static StubElement nonRecBuildStubTreeFor(final PsiElement root, final StubElement rootStub) {
    root.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        StubElement parentStub = getParentStub(element, root, rootStub);
        StubElement stub = parentStub;
        if (element instanceof StubBasedPsiElement) {
          final IStubElementType type = ((StubBasedPsiElement)element).getElementType();

          if (type.shouldCreateStub(element.getNode())) {
            //noinspection unchecked
            stub = type.createStub(element, parentStub);
          }
        }
        else {
          final ASTNode node = element.getNode();
          final IElementType type = node == null? null : node.getElementType();
          if (type instanceof IStubElementType && ((IStubElementType)type).shouldCreateStub(node)) {
            LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + element);
          }
        }

        setParentStubForChildrenOf(element, stub);
        super.visitElement(element);
      }

      @Override
      protected void elementFinished(PsiElement element) {
        setParentStubForChildrenOf(element, null);
      }
    });

    return rootStub;
  }
}
