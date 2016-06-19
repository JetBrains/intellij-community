/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SubstrateRef;
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

/**
 * A base class for PSI elements that support both stub and AST substrates. The purpose of stubs is to hold the most important information
 * (like element names, access modifiers, function parameters etc), save it in the index and use it during code analysis instead of parsing
 * the AST, which can be quite expensive. Ideally, only the files loaded in the editor should ever be parsed, all other information that's
 * needed e.g. for resolving references in those files, should be taken from stub-based PSI.<p/>
 *
 * During indexing, this element is created from text (using {@link #StubBasedPsiElementBase(ASTNode)}),
 * then a stub with relevant information is built from it
 * ({@link IStubElementType#createStub(PsiElement, StubElement)}), and this stub is saved in the
 * index. Then a StubIndex query returns an instance of this element based on a stub
 * (created with {@link #StubBasedPsiElementBase(StubElement, IStubElementType)} constructor. To the clients, such element looks exactly
 * like the one created from AST, all the methods should work the same way.<p/>
 *
 * Code analysis clients should be careful not to invoke methods on this class that can't be implemented using only the information from
 * stubs. Such case is supported: {@link #getNode()} will switch the implementation from stub to AST substrate and get this information, but
 * this is slow performance-wise. So stubs should be designed so that they hold all the information that's relevant for
 * reference resolution and other code analysis.<p/>
 *
 * The subclasses should be careful not to switch to AST prematurely. For example, {@link #getParentByStub()} should be used as much
 * as possible in overridden {@link #getParent()}, and getStubOrPsiChildren methods should be preferred over {@link #getChildren()}.
 */
public class StubBasedPsiElementBase<T extends StubElement> extends ASTDelegatePsiElement {
  public static final Key<String> CREATION_TRACE = Key.create("CREATION_TRACE");
  public static final boolean ourTraceStubAstBinding = "true".equals(System.getProperty("trace.stub.ast.binding", "false"));
  private volatile SubstrateRef mySubstrateRef;
  private final IElementType myElementType;

  public StubBasedPsiElementBase(@NotNull T stub, @NotNull IStubElementType nodeType) {
    mySubstrateRef = new SubstrateRef.StubRef(stub);
    myElementType = nodeType;
  }

  public StubBasedPsiElementBase(@NotNull ASTNode node) {
    mySubstrateRef = SubstrateRef.createAstStrongRef(node);
    myElementType = node.getElementType();
  }

  /**
   * This constructor is created to allow inheriting from this class in JVM languages which doesn't support multiple constructors (e.g. Scala).
   * If your language does support multiple constructors use {@link #StubBasedPsiElementBase(StubElement, IStubElementType)} and
   * {@link #StubBasedPsiElementBase(ASTNode)} instead.
   */
  public StubBasedPsiElementBase(T stub, IElementType nodeType, ASTNode node) {
    if (stub != null) {
      if (nodeType == null) throw new IllegalArgumentException("null cannot be passed to 'nodeType' when 'stub' is non-null");
      if (node != null) throw new IllegalArgumentException("null must be passed to 'node' parameter when 'stub' is non-null");
      mySubstrateRef = new SubstrateRef.StubRef(stub);
      myElementType = nodeType;
    }
    else {
      if (node == null) throw new IllegalArgumentException("'stub' and 'node' parameters cannot be null both");
      if (nodeType != null) throw new IllegalArgumentException("null must be passed to 'nodeType' parameter when 'node' is non-null");
      mySubstrateRef = SubstrateRef.createAstStrongRef(node);
      myElementType = node.getElementType();
    }
  }


  /**
   * Ensures this element is AST-based. This is an expensive operation that might take significant time and allocate lots of objects,
   * so it should be to be avoided if possible.
   *
   * @return an AST node corresponding to this element. If the element is currently operating via stubs,
   * this causes AST to be loaded for the whole file and all stub-based PSI elements in this file (including the current one)
   * to be switched from stub to AST. So, after this call {@link #getStub()} will return null.
   */
  @Override
  @NotNull
  public ASTNode getNode() {
    if (mySubstrateRef instanceof SubstrateRef.StubRef) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      PsiFileImpl file = (PsiFileImpl)getContainingFile();
      if (!file.isValid()) throw new PsiInvalidElementAccessException(this);

      FileElement treeElement = file.getTreeElement();
      if (treeElement != null && mySubstrateRef instanceof SubstrateRef.StubRef) {
        return notBoundInExistingAst(file, treeElement);
      }

      treeElement = file.calcTreeElement();
      if (mySubstrateRef instanceof SubstrateRef.StubRef) {
        return failedToBindStubToAst(file, treeElement);
      }
    }

    return mySubstrateRef.getNode();
  }

  private ASTNode failedToBindStubToAst(@NotNull PsiFileImpl file, @NotNull FileElement fileElement) {
    VirtualFile vFile = file.getVirtualFile();
    StubTree stubTree = file.getStubTree();
    String stubString = stubTree != null ? ((PsiFileStubImpl)stubTree.getRoot()).printTree() : "is null";
    String astString = DebugUtil.treeToString(fileElement, true);
    if (!ourTraceStubAstBinding) {
      stubString = StringUtil.trimLog(stubString, 1024);
      astString = StringUtil.trimLog(astString, 1024);
    }

    @NonNls String message = "Failed to bind stub to AST for element " + getClass() + " in " +
                             (vFile == null ? "<unknown file>" : vFile.getPath()) +
                             "\nFile:\n" + file + "@" + System.identityHashCode(file) +
                             "\nFile stub tree:\n" + stubString +
                             "\nLoaded file AST:\n" + astString;
    if (ourTraceStubAstBinding) {
      message += dumpCreationTraces(fileElement);
    }
    throw new IllegalArgumentException(message);
  }

  @NotNull
  private String dumpCreationTraces(@NotNull FileElement fileElement) {
    final StringBuilder traces = new StringBuilder("\nNow " + Thread.currentThread() + "\n");
    traces.append("My creation trace:\n").append(getUserData(CREATION_TRACE));
    traces.append("AST creation traces:\n");
    fileElement.acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
      @Override
      public void visitComposite(CompositeElement composite) {
        PsiElement psi = composite.getPsi();
        if (psi != null) {
          traces.append(psi).append("@").append(System.identityHashCode(psi)).append("\n");
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

  @SuppressWarnings({"NonConstantStringShouldBeStringBuffer", "StringConcatenationInLoop"})
  private ASTNode notBoundInExistingAst(@NotNull PsiFileImpl file, @NotNull FileElement treeElement) {
    String message = "file=" + file + "; tree=" + treeElement;
    PsiElement each = this;
    while (each != null) {
      message += "\n each of class " + each.getClass() + "; valid=" + each.isValid();
      if (each instanceof StubBasedPsiElementBase) {
        message += "; ref=" + ((StubBasedPsiElementBase)each).mySubstrateRef;
        each = ((StubBasedPsiElementBase)each).getParentByStub();
      }
      else {
        if (each instanceof PsiFile) {
          message += "; same file=" + (each == file) + "; current tree= " + file.getTreeElement() + "; stubTree=" + file.getStubTree() + "; physical=" + file.isPhysical();
        }
        break;
      }
    }
    StubElement eachStub = getStub();
    while (eachStub != null) {
      message += "\n each stub " + (eachStub instanceof PsiFileStubImpl ? ((PsiFileStubImpl)eachStub).getDiagnostics() : eachStub);
      eachStub = eachStub.getParentStub();
    }

    if (ourTraceStubAstBinding) {
      message += dumpCreationTraces(treeElement);
    }
    throw new AssertionError(message);
  }

  /**
   * Don't invoke this method, it's public for implementation reasons.
   */
  public final void setNode(@NotNull ASTNode node) {
    mySubstrateRef = SubstrateRef.createAstStrongRef(node);
  }

  /**
   * Don't invoke this method, it's public for implementation reasons.
   */
  public final void setSubstrateRef(@NotNull SubstrateRef substrateRef) {
    mySubstrateRef = substrateRef;
  }

  /**
   * Don't invoke this method, it's public for implementation reasons.
   */
  @NotNull
  public final SubstrateRef getSubstrateRef() {
    return mySubstrateRef;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return myElementType.getLanguage();
  }

  @Override
  @NotNull
  public PsiFile getContainingFile() {
    try {
      return mySubstrateRef.getContainingFile();
    }
    catch (PsiInvalidElementAccessException e) {
      throw new PsiInvalidElementAccessException(this, e);
    }
  }

  @Override
  public boolean isWritable() {
    return getContainingFile().isWritable();
  }

  @Override
  public boolean isValid() {
    return mySubstrateRef.isValid();
  }

  @Override
  public PsiManagerEx getManager() {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null) {
      return PsiManagerEx.getInstanceEx(project);
    }
    return (PsiManagerEx)getContainingFile().getManager();
  }

  @Override
  @NotNull
  public Project getProject() {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null) {
      return project;
    }
    return getContainingFile().getProject();
  }

  @Override
  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  @Override
  public PsiElement getContext() {
    T stub = getStub();
    if (stub != null) {
      if (!(stub instanceof PsiFileStub)) {
        return stub.getParentStub().getPsi();
      }
    }
    return super.getContext();
  }

  /**
   * In cases when stub hierarchy corresponds to AST hierarchy, i.e. both a parent and its child nodes support stubs, {@link #getParent()}
   * can be implemented efficiently without loading AST, using this method instead. It checks if there is a stub present, takes its parent
   * and returns the PSI corresponding to that parent. Please be careful when using this method and use it only in the described case,
   * because if there are AST-only elements in the hierarchy, it'll return different results depending on whether this element is stub-based
   * or has already been switched to PSI.
   *
   * @return a PSI element taken from parent stub (if present) or parent AST node.
   */
  protected final PsiElement getParentByStub() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    return SharedImplUtil.getParent(getNode());
  }

  /**
   * @return parent PSI element taken from the parent of the corresponding AST node. If AST has not been loaded, it is loaded
   * (in {@link #getNode()}), which might take significant time and memory cost. If possible, {@link #getParentByStub()}
   * should be used instead.
   */
  protected final PsiElement getParentByTree() {
    return SharedImplUtil.getParent(getNode());
  }

  /**
   * By default, delegates to {@link #getParentByTree()} which loads AST and returns parent based on the AST. Can be quite slow, therefore
   * it's advised to override this method in specific implementations and use {@link #getParentByStub()} where possible.
   * @return the parent of this element
   */
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

  /**
   * @return the stub that this element is built upon, or null if the element is currently AST-based. The latter can happen
   * if the file text was loaded from the very beginning, or if it was loaded via {@link #getNode()} on this or any other element
   * in the containing file.
   */
  @Nullable
  public T getStub() {
    ProgressIndicatorProvider.checkCanceled(); // Hope, this is called often
    //noinspection unchecked
    return (T)mySubstrateRef.getStub();
  }

  /**
   * @return a child of specified type, taken from stubs (if this element is currently stub-based) or AST (otherwise).
   */
  @Nullable
  public <Psi extends PsiElement> Psi getStubOrPsiChild(@NotNull IStubElementType<? extends StubElement, Psi> elementType) {
    T stub = getStub();
    if (stub != null) {
      //noinspection unchecked
      final StubElement<Psi> element = stub.findChildStubByType(elementType);
      if (element != null) {
        return element.getPsi();
      }
    }
    else {
      final ASTNode childNode = getNode().findChildByType(elementType);
      if (childNode != null) {
        //noinspection unchecked
        return (Psi)childNode.getPsi();
      }
    }
    return null;
  }

  /**
   * @return a not-null child of specified type, taken from stubs (if this element is currently stub-based) or AST (otherwise).
   */
  @NotNull
  public <S extends StubElement, Psi extends PsiElement> Psi getRequiredStubOrPsiChild(@NotNull IStubElementType<S, Psi> elementType) {
    Psi result = getStubOrPsiChild(elementType);
    assert result != null: "Missing required child of type " + elementType + "; tree: "+DebugUtil.psiToString(this, false);
    return result;
  }

  /**
   * @return children of specified type, taken from stubs (if this element is currently stub-based) or AST (otherwise).
   */
  @NotNull
  public <S extends StubElement, Psi extends PsiElement> Psi[] getStubOrPsiChildren(@NotNull IStubElementType<S, Psi> elementType, @NotNull Psi[] array) {
    T stub = getStub();
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(elementType, array);
    }
    else {
      final ASTNode[] nodes = SharedImplUtil.getChildrenOfType(getNode(), elementType);
      //noinspection unchecked
      Psi[] psiElements = (Psi[])Array.newInstance(array.getClass().getComponentType(), nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        //noinspection unchecked
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  /**
   * @return children of specified type, taken from stubs (if this element is currently stub-based) or AST (otherwise).
   */
  @NotNull
  public <S extends StubElement, Psi extends PsiElement> Psi[] getStubOrPsiChildren(@NotNull IStubElementType<S, Psi> elementType, @NotNull ArrayFactory<Psi> f) {
    T stub = getStub();
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(elementType, f);
    }
    else {
      final ASTNode[] nodes = SharedImplUtil.getChildrenOfType(getNode(), elementType);
      Psi[] psiElements = f.create(nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        //noinspection unchecked
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  /**
   * @return children of specified type, taken from stubs (if this element is currently stub-based) or AST (otherwise).
   */
  @NotNull
  public <Psi extends PsiElement> Psi[] getStubOrPsiChildren(@NotNull TokenSet filter, @NotNull Psi[] array) {
    T stub = getStub();
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(filter, array);
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(filter);
      //noinspection unchecked
      Psi[] psiElements = (Psi[])Array.newInstance(array.getClass().getComponentType(), nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        //noinspection unchecked
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  /**
   * @return children of specified type, taken from stubs (if this element is currently stub-based) or AST (otherwise).
   */
  @NotNull
  public <Psi extends PsiElement> Psi[] getStubOrPsiChildren(@NotNull TokenSet filter, @NotNull ArrayFactory<Psi> f) {
    T stub = getStub();
    if (stub != null) {
      //noinspection unchecked
      return (Psi[])stub.getChildrenByType(filter, f);
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(filter);
      Psi[] psiElements = f.create(nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        //noinspection unchecked
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  /**
   * @return a first ancestor of specified type, in stub hierarchy (if this element is currently stub-based) or AST hierarchy (otherwise).
   */
  @Nullable
  protected <E extends PsiElement> E getStubOrPsiParentOfType(@NotNull Class<E> parentClass) {
    T stub = getStub();
    if (stub != null) {
      //noinspection unchecked
      return (E)stub.getParentStubOfType(parentClass);
    }
    return PsiTreeUtil.getParentOfType(this, parentClass);
  }

  @Override
  protected Object clone() {
    final StubBasedPsiElementBase copy = (StubBasedPsiElementBase)super.clone();
    copy.mySubstrateRef = SubstrateRef.createAstStrongRef(getNode());
    return copy;
  }
}
