// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A convenient interface to implement a serializer that never serializes a single byte.
 * Implementing this interface helps to make the serialized representation more compact, as
 * the size of the stub may be omitted from the stream. Note that if you implement this interface, 
 * you should update the index version, as the serialized representation will change.
 * @param <T>
 */
public interface EmptyStubSerializer<T extends StubElement<?>> extends StubSerializer<T> {
  /**
   * @deprecated Does nothing, as {@link EmptyStubSerializer} should not serialize anything.
   * Do not override or call this method.
   */
  @Deprecated
  @ApiStatus.NonExtendable
  @Override
  default void serialize(@NotNull T stub, @NotNull StubOutputStream dataStream) {
  }

  /**
   * @deprecated delegates to {@link #instantiate(StubElement)}, as it does not depend on
   * {@code dataStream} parameter. Do not override or call this method.
   */
  @Deprecated
  @ApiStatus.NonExtendable
  @Override
  default @NotNull T deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) {
    return instantiate(parentStub);
  }

  /**
   * Instantiates the stub given the supplied parent stub.
   * 
   * @param parentStub parent stub to use 
   * @return newly instantiated stub with the specified parent set.
   */
  @NotNull T instantiate(StubElement<?> parentStub);
}
