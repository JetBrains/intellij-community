// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class is intended to manage Stub Serializers {@link ObjectStubSerializer} and stub serialization/deserialization algorithm.
 */
@ApiStatus.Internal
public abstract class SerializationManagerEx implements StubTreeSerializer {
  public static SerializationManagerEx getInstanceEx() {
    return ServiceManager.getService(SerializationManagerEx.class);
  }

  /**
   * @deprecated only kept to support prebuilt stubs
   */
  @Deprecated
  public abstract void reSerialize(@NotNull InputStream inStub,
                                   @NotNull OutputStream outStub,
                                   @NotNull StubTreeSerializer newSerializationManager) throws IOException;

  protected abstract void initSerializers();

  public abstract boolean isNameStorageCorrupted();

  public abstract void repairNameStorage(@NotNull Exception corruptionCause);

  /**
   * @deprecated use {@link SerializationManagerEx#repairNameStorage(Exception)}
   * with specified corruption cause
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void repairNameStorage() {
    repairNameStorage(new Exception());
  }

  public abstract void flushNameStorage() throws IOException;

  public abstract void reinitializeNameStorage();
}
