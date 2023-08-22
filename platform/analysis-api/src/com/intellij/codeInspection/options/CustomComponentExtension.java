// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension that describes custom components of specific kind
 */
@ApiStatus.Experimental
public abstract class CustomComponentExtension<T> {
  public static final ExtensionPointName<CustomComponentExtension<?>> EP_NAME = ExtensionPointName.create("com.intellij.inspectionCustomComponent");

  private final @NotNull @NonNls String myId;

  protected CustomComponentExtension(@NotNull @NonNls String id) { 
    myId = id; 
  }
  
  public static @Nullable CustomComponentExtension<?> find(@NotNull @NonNls String id) {
    return EP_NAME.getByKey(id, CustomComponentExtension.class, CustomComponentExtension::componentId);
  }

  public @NotNull @NonNls String componentId() {
    return myId;
  }

  public @NotNull @NonNls String serializeData(T t) {
    return "";
  }

  public T deserializeData(@NotNull @NonNls String data) {
    if (!data.isEmpty()) throw new IllegalArgumentException();
    return null;
  }
  
  public @NotNull OptCustom component(T t) {
    return new OptCustom(componentId(), serializeData(t));
  }
}
