// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public interface Stub {
  Stub getParentStub();

  @NotNull List<? extends Stub> getChildrenStubs();

  /**
   * @deprecated use {@link #getStubSerializer} or {@link StubElement#getElementType()} instead
   */
  @Deprecated
  ObjectStubSerializer<?, ? extends Stub> getStubType();

  default ObjectStubSerializer<?, ? extends Stub> getStubSerializer() {
    return getStubType();
  }
}
