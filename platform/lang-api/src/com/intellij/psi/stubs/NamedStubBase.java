// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.stubs;

import com.intellij.psi.PsiNamedElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class NamedStubBase<T extends PsiNamedElement> extends StubBase<T> implements NamedStub<T> {
  @Nullable private final StringRef myName;

  protected NamedStubBase(StubElement parent, @NotNull IStubElementType elementType, @Nullable StringRef name) {
    super(parent, elementType);
    myName = name;
  }

  protected NamedStubBase(final StubElement parent, @NotNull IStubElementType elementType, @Nullable String name) {
    this(parent, elementType, StringRef.fromString(name));
  }

  @Override
  @Nullable
  public String getName() {
    return myName != null ? myName.getString() : null;
  }
}
