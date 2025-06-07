// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.stubs;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class NamedStubBase<T extends PsiNamedElement> extends StubBase<T> implements NamedStub<T> {
  private final @Nullable StringRef myName;

  protected NamedStubBase(StubElement parent, @NotNull IStubElementType elementType, @Nullable StringRef name) {
    super(parent, elementType);
    myName = name;
  }

  @ApiStatus.Experimental
  protected NamedStubBase(StubElement parent, @NotNull IElementType elementType, @Nullable StringRef name) {
    super(parent, elementType);
    myName = name;
  }

  protected NamedStubBase(final StubElement parent, @NotNull IStubElementType elementType, @Nullable String name) {
    this(parent, elementType, StringRef.fromString(name));
  }

  @Override
  public @Nullable String getName() {
    return myName != null ? myName.getString() : null;
  }
}
