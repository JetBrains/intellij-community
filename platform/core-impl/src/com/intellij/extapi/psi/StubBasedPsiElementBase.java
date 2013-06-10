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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;

public class StubBasedPsiElementBase<T extends StubElement> extends ASTDelegatePsiElement {
  public static final Key<String> CREATION_TRACE = Key.create("CREATION_TRACE");
  private static final Logger LOG = Logger.getInstance("#com.intellij.extapi.psi.StubBasedPsiElementBase");
  public static final boolean ourTraceStubAstBinding = "true".equals(System.getProperty("trace.stub.ast.binding", "false"));
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

  @Override
  @NotNull
  public ASTNode getNode() {
    ASTNode node = myNode;
    if (node == null) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      PsiFileImpl file = (PsiFileImpl)getContainingFile();
      if (!file.isValid()) throw new PsiInvalidElementAccessException(this);

      FileElement treeElement = file.getTreeElement();
      StubTree stubTree = file.getStubTree();
      if (treeElement != null && myNode == null) {
        return notBoundInExistingAst(file, treeElement, stubTree);
      }

      final FileElement fileElement = file.calcTreeElement();
      node = myNode;
      if (node == null) {
        return failedToBindStubToAst(file, stubTree, fileElement);
      }
    }

    return node;
  }

  private ASTNode failedToBindStubToAst(PsiFileImpl file, StubTree stubTree, FileElement fileElement) {
    VirtualFile vFile = file.getVirtualFile();
    String stubString = stubTree != null ? ((PsiFileStubImpl)stubTree.getRoot()).printTree() : "is null";
    String astString = DebugUtil.treeToString(fileElement, true);
    if (!ourTraceStubAstBinding) {
      stubString = StringUtil.trimLog(stubString, 1024);
      astString = StringUtil.trimLog(astString, 1024);
    }

    @NonNls String message = "Failed to bind stub to AST for element " + getClass() + " in " +
                             (vFile == null ? "<unknown file>" : vFile.getPath()) +
                             "\nFile:\n" + file.toString() + "@" + System.identityHashCode(file) +
                             "\nFile stub tree:\n" + stubString +
                             "\nLoaded file AST:\n" + astString;
    if (ourTraceStubAstBinding) {
      message += dumpCreationTraces(fileElement);
    }
    throw new IllegalArgumentException(message);
  }

  private String dumpCreationTraces(FileElement fileElement) {
    final StringBuilder traces = new StringBuilder("\nNow " + Thread.currentThread() + "\n");
    traces.append("My creation trace:\n").append(getUserData(CREATION_TRACE));
    traces.append("AST creation traces:\n");
    fileElement.acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
      @Override
      public void visitComposite(CompositeElement composite) {
        PsiElement psi = composite.getPsi();
        if (psi != null) {
          traces.append(psi.toString()).append("@").append(System.identityHashCode(psi)).append("\n");
          String trace = psi.getUserData(CREATION_TRACE);
          if (trace != null) {
            traces.append(trace).append("\n");
          }
        }
        super.visitComposite(composite);
      }
    });
    return traces.toString();
  }

  private ASTNode notBoundInExistingAst(PsiFileImpl file, FileElement treeElement, StubTree stubTree) {
    @NonNls String message = "this=" + this.getClass() + "; file.isPhysical=" + file.isPhysical() + "; node=" + myNode + "; file=" + file +
                             "; tree=" + treeElement + "; stubTree=" + stubTree;
    PsiElement each = this;
    while (each != null) {
      message += "\n each of class " + each.getClass();
      if (each instanceof StubBasedPsiElementBase) {
        message += "; node=" + ((StubBasedPsiElementBase)each).myNode + "; stub=" + ((StubBasedPsiElementBase)each).myStub;
        each = ((StubBasedPsiElementBase)each).getParentByStub();
      } else {
        break;
      }
    }
    if (ourTraceStubAstBinding) {
      message += dumpCreationTraces(treeElement);
    }
    throw new AssertionError(message);
  }

  public final void setNode(final ASTNode node) {
    myNode = node;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return myElementType.getLanguage();
  }

  @Override
  @NotNull
  public PsiFile getContainingFile() {
    StubElement stub = myStub;
    if (stub != null) {
      while (!(stub instanceof PsiFileStub)) {
        stub = stub.getParentStub();
      }
      PsiFile psi = (PsiFile)stub.getPsi();
      if (psi != null) {
        return psi;
      }
      ApplicationManager.getApplication().assertReadAccessAllowed();
      synchronized (PsiLock.LOCK) {
        if (myStub != null) {
          throw new PsiInvalidElementAccessException(this, "no psi for file stub " + stub, null);
        }
      }
    }

    PsiFile file = super.getContainingFile();
    if (file == null) {
      throw new PsiInvalidElementAccessException(this);
    }

    return file;
  }

  @Override
  public boolean isWritable() {
    return getContainingFile().isWritable();
  }

  @Override
  public boolean isValid() {
    T stub = myStub;
    if (stub != null) {
      StubElement parent = stub.getParentStub();
      if (parent == null) {
        LOG.error("No parent for stub " + stub + " of class " + stub.getClass());
        return false;
      }
      PsiElement psi = parent.getPsi();
      return psi != null && psi.isValid();
    }

    return super.isValid();
  }

  @Override
  public PsiManagerEx getManager() {
    return (PsiManagerEx)getContainingFile().getManager();
  }

  @Override
  @NotNull
  public Project getProject() {
    return getContainingFile().getProject();
  }

  @Override
  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  @Override
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

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    setStub(null);
  }

  protected final PsiElement getParentByTree() {
    return SharedImplUtil.getParent(getNode());
  }

  @Override
  public PsiElement getParent() {
    return getParentByTree();
  }

  @NotNull
  public IStubElementType getElementType() {
    if (!(myElementType instanceof IStubElementType)) {
      throw new AssertionError("Not a stub type: " + myElementType + " in " + getClass());
    }
    return (IStubElementType)myElementType;
  }

  public T getStub() {
    ProgressIndicatorProvider.checkCanceled(); // Hope, this is called often
    return myStub;
  }

  public final void setStub(@Nullable T stub) {
    myStub = stub;
  }

  @Nullable
  public <Psi extends PsiElement> Psi getStubOrPsiChild(final IStubElementType<? extends StubElement, Psi> elementType) {
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

  @Nullable
  protected PsiElement getStubOrPsiParent() {
    T stub = myStub;
    if (stub != null) {
      //noinspection unchecked
      return stub.getParentStub().getPsi();
    }
    return getParent();
  }

  @Override
  protected Object clone() {
    final StubBasedPsiElementBase stubbless = (StubBasedPsiElementBase)super.clone();
    stubbless.myStub = null;
    return stubbless;
  }
}
