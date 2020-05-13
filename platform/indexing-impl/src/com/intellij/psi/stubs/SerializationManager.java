// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public abstract class SerializationManager {
  public static SerializationManager getInstance() {
    return ApplicationManager.getApplication().getService(SerializationManager.class);
  }

  /**
   * Use {@link StubElementTypeHolderEP} to register stub serializer instead of manual registration.
   */
  @ApiStatus.Internal
  public void registerSerializer(ObjectStubSerializer serializer) {
    registerSerializer(serializer.getExternalId(), new Computable.PredefinedValueComputable<>(serializer));
  }

  /**
   * Use {@link StubElementTypeHolderEP} to register stub serializer instead of manual registration.
   */
  @ApiStatus.Internal
  protected abstract void registerSerializer(String externalId, Computable<ObjectStubSerializer> lazySerializer);

  @Nullable
  @Contract("null -> null")
  public abstract String internString(@Nullable String string);
}