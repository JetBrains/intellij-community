// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.*;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.StackOverflowPreventedException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FileElement extends LazyParseableElement implements FileASTNode {
  public static final FileElement[] EMPTY_ARRAY = new FileElement[0];
  private volatile CharTable myCharTable = new CharTableImpl();
  private volatile boolean myDetached;
  private volatile AstSpine myStubbedSpine;

  @Override
  protected PsiElement createPsiNoLock() {
    return myDetached ? null : super.createPsiNoLock();
  }

  public void detachFromFile() {
    myDetached = true;
    clearPsi();
  }

  @Override
  public @NotNull CharTable getCharTable() {
    return myCharTable;
  }

  @Override
  public @NotNull LighterAST getLighterAST() {
    IElementType contentType = getElementType();
    if (!isParsed() && contentType instanceof ILightStubFileElementType) {
      return new FCTSBackedLighterAST(getCharTable(), ((ILightStubFileElementType<?>)contentType).parseContentsLight(this));
    }
    return new TreeBackedLighterAST(this);
  }

  public FileElement(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Override
  public PsiManagerEx getManager() {
    CompositeElement treeParent = getTreeParent();
    if (treeParent != null) return treeParent.getManager();
    PsiElement psi = getPsi();
    if (psi == null) throw PsiInvalidElementAccessException.createByNode(this, null);
    return (PsiManagerEx)psi.getManager();
  }

  @Override
  public ASTNode copyElement() {
    PsiFileImpl psiElement = (PsiFileImpl)getPsi();
    PsiFileImpl psiElementCopy = (PsiFileImpl)psiElement.copy();
    return psiElementCopy.getTreeElement();
  }

  public void setCharTable(@NotNull CharTable table) {
    myCharTable = table;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myStubbedSpine = null;
  }

  @ApiStatus.Internal
  public final @NotNull AstSpine getStubbedSpine() {
    AstSpine result = myStubbedSpine;
    if (result == null) {
      PsiFileImpl file = (PsiFileImpl)getPsi();
      IStubFileElementType type = file.getElementTypeForStubBuilder();
      if (type == null) return AstSpine.EMPTY_SPINE;

      result = RecursionManager.doPreventingRecursion(file, false, () -> new AstSpine(calcStubbedDescendants(type.getBuilder())));
      if (result == null) {
        throw new StackOverflowPreventedException("Endless recursion prevented");
      }
      myStubbedSpine = result;
    }
    return result;
  }

  private List<CompositeElement> calcStubbedDescendants(StubBuilder builder) {
    List<CompositeElement> result = new ArrayList<>();
    result.add(this);

    acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitComposite(CompositeElement node) {
        CompositeElement parent = node.getTreeParent();
        if (parent != null && builder.skipChildProcessingWhenBuildingStubs(parent, node)) {
          return;
        }

        IElementType type = node.getElementType();
        if (type instanceof IStubElementType && ((IStubElementType<?, ?>)type).shouldCreateStub(node)) {
          result.add(node);
        }

        super.visitNode(node);
      }
    });
    return result;
  }

}
