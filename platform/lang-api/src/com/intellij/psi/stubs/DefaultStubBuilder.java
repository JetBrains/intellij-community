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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.StubBuilder;

public class DefaultStubBuilder implements StubBuilder {
  public StubElement buildStubTree(final PsiFile file) {
    return buildStubTreeFor(file, createStubForFile(file));
  }

  protected StubElement createStubForFile(final PsiFile file) {
    return new PsiFileStubImpl(file);
  }

  protected static StubElement buildStubTreeFor(PsiElement elt, StubElement parentStub) {
    StubElement stub = parentStub;
    if (elt instanceof StubBasedPsiElement) {
      final IStubElementType type = ((StubBasedPsiElement)elt).getElementType();

      if (type.shouldCreateStub(elt.getNode())) {
        //noinspection unchecked
        stub = type.createStub(elt, parentStub);
      }
    }

    final PsiElement[] psiElements = elt.getChildren();
    for (PsiElement child : psiElements) {
      buildStubTreeFor(child, stub);
    }

    return stub;
  }

}
