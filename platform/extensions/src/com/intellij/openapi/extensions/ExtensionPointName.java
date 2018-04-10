// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class ExtensionPointName<T> {
  private final String myName;

  public ExtensionPointName(@NotNull @NonNls final String name) {
    myName = name;
  }

  @NotNull
  public static <T> ExtensionPointName<T> create(@NotNull @NonNls final String name) {
    return new ExtensionPointName<>(name);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return myName;
  }

  @NotNull
  public T[] getExtensions() {
    return Extensions.getExtensions(this);
  }

  @NotNull
  public T[] getExtensions(AreaInstance areaInstance) {
    return Extensions.getExtensions(this, areaInstance);
  }

  public <V extends T> V findExtension(Class<V> instanceOf) {
    return ContainerUtil.findInstance(getExtensions(), instanceOf);
  }
}