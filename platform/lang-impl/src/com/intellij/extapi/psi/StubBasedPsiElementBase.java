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
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.progress.NonCancelableSection;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;

public class StubBasedPsiElementBase<T extends StubElement> extends ASTDelegatePsiElement {
  private volatile T myStub;
  private volatile ASTNode myNode;
  private final IElementType myElementType;

  public StubBasedPsiElementBase(@NotNull T stub, @NotNull IStubElementType nodeType) {
    myStub = stub;
    myElementType = nodeType;
    myNode = null;
  }

  public StubBasedPsiElementBase(@NotNull ASTNode node) {
    myNode = node;
    myElementType = node.getElementType();
  }

  @NotNull
  public ASTNode getNode() {
    ASTNode node = myNode;
    if (node == null) {
      PsiFileImpl file = (PsiFileImpl)getContainingFile();
      synchronized (file.getStubLock()) {
        node = myNode;
        if (node == null) {
          NonCancelableSection criticalSection = ProgressManager.getInstance().startNonCancelableSection();
          try {
            if (!file.isValid()) throw new PsiInvalidElementAccessException(this);
            assert file.getTreeElement() == null;
            StubTree stubTree = file.getStubTree();
            final FileElement fileElement = file.loadTreeElement();
            node = myNode;
            if (node == null) {
              String message = new StringBuilder().
                append("failed to bind stub to AST for element ").
                append(getClass()).
                append(" in ").
                append(file.getVirtualFile() == null ? "<unknown file>" : file.getVirtualFile().getPath()).
                append("\nFile stub tree:\n").
                append(stubTree != null ? ((PsiFileStubImpl)stubTree.getRoot()).printTree() : " is null").
                append("\nLoaded file AST:\n").
                append(DebugUtil.treeToString(fileElement, true)).
                toString();
              throw new IllegalArgumentException(message);
            }
          }
          finally {
            criticalSection.done();
          }
        }
      }
    }

    return node;
  }

  public void setNode(final ASTNode node) {
    myNode = node;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return myElementType.getLanguage();
  }

  public PsiFile getContainingFile() {
    StubElement stub = myStub;
    if (stub != null) {
      while (!(stub instanceof PsiFileStub)) {
        stub = stub.getParentStub();
      }
      return (PsiFile)((PsiFileStub)stub).getPsi();
    }

    return super.getContainingFile();
  }

  public boolean isWritable() {
    return getContainingFile().isWritable();
  }

  public boolean isValid() {
    T stub = myStub;
    if (stub != null) {
      if (stub instanceof PsiFileStub) {
        return stub.getPsi().isValid();
      }

      return stub.getParentStub().getPsi().isValid();
    }

    return super.isValid();
  }

  public PsiManagerEx getManager() {
    return (PsiManagerEx)getContainingFile().getManager();
  }

  @NotNull
  public Project getProject() {
    return getContainingFile().getProject();
  }

  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  public PsiElement getContext() {
    T stub = myStub;
    if (stub != null) {
      if (!(stub instanceof PsiFileStub)) {
        return stub.getParentStub().getPsi();
      }
    }
    return super.getContext();
  }

  protected final PsiElement getParentByStub() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    return SharedImplUtil.getParent(getNode());
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    setStub(null);
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(getNode());
  }

  @NotNull
  public IStubElementType getElementType() {
    return (IStubElementType)myElementType;
  }

  public T getStub() {
    ProgressManager.checkCanceled(); // Hope, this is called often
    return myStub;
  }

  public void setStub(T stub) {
    myStub = stub;
  }

  @Nullable
  public <Stub extends StubElement, Psi extends PsiElement> Psi getStubOrPsiChild(final IStubElementType<Stub, Psi> elementType) {
    T stub = myStub;
    if (stub != null) {
      final StubElement<Psi> element = stub.findChildStubByType(elementType);
      if (element != null) {
        return element.getPsi();
      }
    }
    else {
      final ASTNode childNode = getNode().findChildByType(elementType);
      if (childNode != null) {
        return (Psi)childNode.getPsi();
      }
    }
    return null;
  }

  @NotNull
  public <Stub extends StubElement, Psi extends PsiElement> Psi getRequiredStubOrPsiChild(final IStubElementType<Stub, Psi> elementType) {
    Psi result = getStubOrPsiChild(elementType);
    assert result != null: "Missing required child of type " + elementType + "; tree: "+DebugUtil.psiToString(this, false);
    return result;
  }


  public <Stub extends StubElement, Psi extends PsiElement> Psi[] getStubOrPsiChildren(final IStubElementType<Stub, Psi> elementType, Psi[] array) {
    T stub = myStub;
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(elementType, array);
    }
    else {
      final ASTNode[] nodes = SharedImplUtil.getChildrenOfType(getNode(), elementType);
      Psi[] psiElements = (Psi[])Array.newInstance(array.getClass().getComponentType(), nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  public <Stub extends StubElement, Psi extends PsiElement> Psi[] getStubOrPsiChildren(final IStubElementType<Stub, Psi> elementType, ArrayFactory<Psi> f) {
    T stub = myStub;
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(elementType, f);
    }
    else {
      final ASTNode[] nodes = SharedImplUtil.getChildrenOfType(getNode(), elementType);
      Psi[] psiElements = f.create(nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  public <Psi extends PsiElement> Psi[] getStubOrPsiChildren(TokenSet filter, Psi[] array) {
    T stub = myStub;
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(filter, array);
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(filter);
      Psi[] psiElements = (Psi[])Array.newInstance(array.getClass().getComponentType(), nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  public <Psi extends PsiElement> Psi[] getStubOrPsiChildren(TokenSet filter, ArrayFactory<Psi> f) {
    T stub = myStub;
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(filter, f);
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(filter);
      Psi[] psiElements = f.create(nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  @Nullable
  protected <E extends PsiElement> E getStubOrPsiParentOfType(final Class<E> parentClass) {
    T stub = myStub;
    if (stub != null) {
      //noinspection unchecked
      return (E)stub.getParentStubOfType(parentClass);
    }
    return PsiTreeUtil.getParentOfType(this, parentClass);
  }

  protected Object clone() {
    final StubBasedPsiElementBase stubbless = (StubBasedPsiElementBase)super.clone();
    stubbless.myStub = null;
    return stubbless;
  }
}
