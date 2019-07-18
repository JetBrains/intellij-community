// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * For project level extension points please use {@link ProjectExtensionPointName}.
 */
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

  public void forEachExtensionSafe(Consumer<T> consumer) {
    getPoint(null).forEachExtensionSafe(consumer);
  }

  @NotNull
  public List<T> getExtensionsIfPointIsRegistered() {
    return getExtensionsIfPointIsRegistered(null);
  }

  @NotNull
  public List<T> getExtensionsIfPointIsRegistered(@Nullable AreaInstance areaInstance) {
    ExtensionPoint<T> point = Extensions.getArea(areaInstance).getExtensionPointIfRegistered(getName());
    return point == null ? Collections.emptyList() : point.getExtensionList();
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
    return findExtension(this, instanceOf, null, false);
  }

  @NotNull
  public <V extends T> V findExtensionOrFail(@NotNull Class<V> instanceOf) {
    //noinspection ConstantConditions
    return findExtension(this, instanceOf, null, true);
  }

  @NotNull
  public <V extends T> V findExtensionOrFail(@NotNull Class<V> instanceOf, @Nullable AreaInstance areaInstance) {
    //noinspection ConstantConditions
    return findExtension(this, instanceOf, areaInstance, true);
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behaviour is not predictable -
   * events will be fired during iteration and probably it will be not expected.
   *
   * Use only for interface extension points, not for bean.
   *
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case stop iteration).
   *
   * Possible use cases:
   * 1. Conditional iteration (no need to create all extensions if iteration will be stopped due to some condition).
   * 2. Iterated only once per application (no need to cache extension list internally).
   */
  @NotNull
  @ApiStatus.Experimental
  public Iterable<T> getIterable(@Nullable AreaInstance areaInstance) {
    return ((ExtensionPointImpl<T>)getPoint(areaInstance));
  }

  @NotNull
  @ApiStatus.Experimental
  public Iterable<T> getIterable() {
    return getIterable(null);
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public void processWithPluginDescriptor(@NotNull BiConsumer<T, PluginDescriptor> consumer) {
    (((ExtensionPointImpl<T>)getPoint(null))).processWithPluginDescriptor(consumer);
  }
}
