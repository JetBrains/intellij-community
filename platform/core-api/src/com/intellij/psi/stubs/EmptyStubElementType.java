// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class EmptyStubElementType<T extends PsiElement> extends IStubElementType<EmptyStub<?>, T> {
  protected EmptyStubElementType(@NotNull @NonNls String debugName, @Nullable Language language) {
    super(debugName, language);
  }

  @NotNull
  @Override
  public final EmptyStub<?> createStub(@NotNull T psi, StubElement<?> parentStub) {
    return createStub(parentStub);
  }

  protected EmptyStub<?> createStub(StubElement parentStub) {
    return new EmptyStub(parentStub, this);
  }

  @NotNull
  @Override
  public String getExternalId() {
    return getLanguage().getID() + toString();
  }

  @Override
  public final void serialize(@NotNull EmptyStub stub, @NotNull StubOutputStream dataStream) {
  }

  @NotNull
  @Override
  public final EmptyStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) {
    return createStub(parentStub);
  }

  @Override
  public final void indexStub(@NotNull EmptyStub stub, @NotNull IndexSink sink) {
  }
}
