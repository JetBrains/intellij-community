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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.tree.IElementType;

public class DefaultStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.DefaultStubBuilder");

  public StubElement buildStubTree(final PsiFile file) {
    return buildStubTreeFor(file, createStubForFile(file));
  }

  protected StubElement createStubForFile(final PsiFile file) {
    return new PsiFileStubImpl(file);
  }

  protected StubElement buildStubTreeFor(PsiElement elt, StubElement parentStub) {
    StubElement stub = parentStub;
    IElementType eltType;
    if (elt instanceof StubBasedPsiElement) {
      final IStubElementType type = ((StubBasedPsiElement)elt).getElementType();

      if (type.shouldCreateStub(elt.getNode())) {
        //noinspection unchecked
        stub = type.createStub(elt, parentStub);
      }
      eltType = type;
    }
    else {
      final ASTNode node = elt.getNode();
      final IElementType type = node == null? null : node.getElementType();
      if (type instanceof IStubElementType && ((IStubElementType)type).shouldCreateStub(node)) {
        LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + elt);
      }
      eltType = type;
    }

    for (PsiElement child = elt.getFirstChild(); child != null; child = child.getNextSibling()) {
      ASTNode childNode = child.getNode();
      if (!skipChildProcessingWhenBuildingStubs(eltType, childNode != null ? childNode.getElementType():null)) {
        buildStubTreeFor(child, stub);
      }
    }

    return stub;
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(IElementType nodeType, IElementType childType) {
    return false;
  }
}
