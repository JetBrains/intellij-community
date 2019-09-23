// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseExtensionPointName<T> {
  private final String myName;

  public BaseExtensionPointName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  public final String toString() {
    return myName;
  }

  @NotNull
  protected final ExtensionPointImpl<T> getPointImpl(@Nullable AreaInstance areaInstance) {
    ExtensionsAreaImpl area = (ExtensionsAreaImpl)(areaInstance == null ? Extensions.getRootArea() : areaInstance.getExtensionArea());
    return area.getExtensionPoint(getName());
  }
}
