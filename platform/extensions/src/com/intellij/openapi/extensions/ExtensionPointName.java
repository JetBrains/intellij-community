// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author mike
 */
public final class ExtensionPointName<T> {
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
    return getExtensions(null);
  }

  @NotNull
  public List<T> getExtensionList() {
    return getExtensionList(null);
  }

  @NotNull
  public List<T> getExtensionList(@Nullable AreaInstance areaInstance) {
    return getPoint(areaInstance).getExtensionList();
  }

  @NotNull
  public T[] getExtensions(@Nullable AreaInstance areaInstance) {
    return getPoint(areaInstance).getExtensions();
  }

  @NotNull
  public ExtensionPoint<T> getPoint(@Nullable AreaInstance areaInstance) {
    return Extensions.getArea(areaInstance).getExtensionPoint(getName());
  }

  public <V extends T> V findExtension(@NotNull Class<V> instanceOf) {
    return ContainerUtil.findInstance(getExtensionList(), instanceOf);
  }
}