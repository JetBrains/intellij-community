// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.ILightStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public abstract class JavaStubElementType<StubT extends StubElement<?>, PsiT extends PsiElement>
    extends ILightStubElementType<StubT, PsiT> implements ICompositeElementType, ParentProviderElementType {
  private final boolean myLeftBound;

  private final Set<IElementType> myParentElementTypes;
  protected JavaStubElementType(@NotNull @NonNls final String debugName, @NotNull IElementType parentElementType) {
    this(debugName, false, parentElementType);
  }

  protected JavaStubElementType(@NotNull @NonNls final String debugName, final boolean leftBound, @NotNull IElementType parentElementType) {
    super(debugName, JavaLanguage.INSTANCE);
    myLeftBound = leftBound;
    myParentElementTypes = Collections.singleton(parentElementType);
  }

  @Override
  public Set<IElementType> getParents() {
    return myParentElementTypes;
  }

  @NotNull
  @Override
  public String getExternalId() {
    return "java." + toString();
  }

  protected StubPsiFactory getPsiFactory(StubT stub) {
    return getFileStub(stub).getPsiFactory();
  }

  public boolean isCompiled(StubT stub) {
    return getFileStub(stub).isCompiled();
  }

  private PsiJavaFileStub getFileStub(StubT stub) {
    StubElement<?> parent = stub;
    while (!(parent instanceof PsiFileStub)) {
      parent = parent.getParentStub();
    }

    return (PsiJavaFileStub)parent;
  }

  public abstract PsiT createPsi(@NotNull ASTNode node);

  @Override
  public final @NotNull StubT createStub(@NotNull PsiT psi, StubElement<? extends PsiElement> parentStub) {
    final String message = "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }

  @Override
  public boolean isLeftBound() {
    return myLeftBound;
  }
}
