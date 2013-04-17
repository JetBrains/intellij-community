/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.ILightStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class JavaStubElementType<StubT extends StubElement, PsiT extends PsiElement>
    extends ILightStubElementType<StubT, PsiT> implements ICompositeElementType {
  private final boolean myLeftBound;

  protected JavaStubElementType(@NotNull @NonNls final String debugName) {
    this(debugName, false);
  }

  protected JavaStubElementType(@NotNull @NonNls final String debugName, final boolean leftBound) {
    super(debugName, JavaLanguage.INSTANCE);
    myLeftBound = leftBound;
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
    StubElement parent = stub;
    while (!(parent instanceof PsiFileStub)) {
      parent = parent.getParentStub();
    }

    return (PsiJavaFileStub)parent;
  }

  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  public abstract PsiT createPsi(@NotNull ASTNode node);

  @Override
  public final StubT createStub(@NotNull final PsiT psi, final StubElement parentStub) {
    final String message = "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }

  @Override
  public boolean isLeftBound() {
    return myLeftBound;
  }
}
