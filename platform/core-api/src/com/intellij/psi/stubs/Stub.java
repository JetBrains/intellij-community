// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @ApiStatus.Experimental
  default ObjectStubSerializer<?, ? extends Stub> getStubSerializer() {
    return getStubType();
  }
}
