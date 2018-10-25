// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public final class ExtensionPointName<T> extends BaseExtensionPointName {
  public ExtensionPointName(@NotNull String name) {
    super(name);
  }

  @NotNull
  public static <T> ExtensionPointName<T> create(@NotNull @NonNls final String name) {
    return new ExtensionPointName<>(name);
  }

  /**
   * Prefer to use {@link #getExtensionList()}.
   */
  @NotNull
  public T[] getExtensions() {
    return getPoint(null).getExtensions();
  }

  @NotNull
  public List<T> getExtensionList() {
    return getPoint(null).getExtensionList();
  }

  @NotNull
  public Stream<T> extensions() {
    return getPoint(null).extensions();
  }

  public boolean hasAnyExtensions() {
    return getPoint(null).hasAnyExtensions();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @NotNull
  public List<T> getExtensionList(@Nullable AreaInstance areaInstance) {
    return getPoint(areaInstance).getExtensionList();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @NotNull
  public T[] getExtensions(@Nullable AreaInstance areaInstance) {
    return getPoint(areaInstance).getExtensions();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#extensions(AreaInstance)}
   */
  @NotNull
  public Stream<T> extensions(@Nullable AreaInstance areaInstance) {
    return getPoint(areaInstance).extensions();
  }

  @NotNull
  public ExtensionPoint<T> getPoint(@Nullable AreaInstance areaInstance) {
    return Extensions.getArea(areaInstance).getExtensionPoint(getName());
  }

  @Nullable
  public <V extends T> V findExtension(@NotNull Class<V> instanceOf) {
    return ContainerUtil.findInstance(getExtensionList(), instanceOf);
  }

  @NotNull
  public <V extends T> V findExtensionOrFail(@NotNull Class<V> instanceOf) {
    V result = findExtension(instanceOf);
    if (result == null) {
      throw new IllegalArgumentException("could not find extension implementation " + instanceOf);
    }
    return result;
  }
}