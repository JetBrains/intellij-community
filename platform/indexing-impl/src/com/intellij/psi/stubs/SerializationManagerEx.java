// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class is intended to manage Stub Serializers {@link ObjectStubSerializer} and stub serialization/deserialization algorithm.
 */
@ApiStatus.Internal
public abstract class SerializationManagerEx extends SerializationManager {
  public static SerializationManagerEx getInstanceEx() {
    return (SerializationManagerEx)SerializationManager.getInstance();
  }

  public abstract void serialize(@NotNull Stub rootStub, @NotNull OutputStream stream);

  @NotNull
  public abstract Stub deserialize(@NotNull InputStream stream) throws SerializerNotFoundException;

  @ApiStatus.Experimental
  public abstract void reSerialize(@NotNull InputStream inStub,
                                   @NotNull OutputStream outStub,
                                   @NotNull SerializationManagerEx newSerializationManager) throws IOException;

  protected abstract void initSerializers();

  public abstract boolean isNameStorageCorrupted();

  public abstract void repairNameStorage();

  public abstract void flushNameStorage();

  public abstract void reinitializeNameStorage();
}
