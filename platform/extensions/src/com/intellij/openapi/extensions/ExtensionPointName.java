// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * For project level extension points please use {@link ProjectExtensionPointName}.
 */
public final class ExtensionPointName<T> extends BaseExtensionPointName<T> {
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
    return getPointImpl(null).getExtensions();
  }

  @NotNull
  public List<T> getExtensionList() {
    return getPointImpl(null).getExtensionList();
  }

  /**
   * Invokes the given consumer for each extension registered in this extension point. Logs exceptions thrown by the consumer.
   */
  public void forEachExtensionSafe(@NotNull Consumer<? super T> consumer) {
    ExtensionProcessingHelper.forEachExtensionSafe(consumer, getPointImpl(null));
  }

  @Nullable
  public T findFirstSafe(@NotNull Predicate<? super T> predicate) {
    return ExtensionProcessingHelper.findFirstSafe(predicate, getPointImpl(null));
  }

  @Nullable
  public <R> R computeSafeIfAny(@NotNull Function<T, R> processor) {
    return ExtensionProcessingHelper.computeSafeIfAny(processor, getPointImpl(null));
  }

  @NotNull
  public List<T> getExtensionsIfPointIsRegistered() {
    return getExtensionsIfPointIsRegistered(null);
  }

  @NotNull
  public List<T> getExtensionsIfPointIsRegistered(@Nullable AreaInstance areaInstance) {
    ExtensionsArea area = areaInstance == null ? Extensions.getRootArea() : areaInstance.getExtensionArea();
    ExtensionPoint<T> point = area == null ? null : area.getExtensionPointIfRegistered(getName());
    return point == null ? Collections.emptyList() : point.getExtensionList();
  }

  @NotNull
  public Stream<T> extensions() {
    return getPointImpl(null).extensions();
  }

  public boolean hasAnyExtensions() {
    return getPointImpl(null).hasAnyExtensions();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @NotNull
  public List<T> getExtensionList(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance).getExtensionList();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @NotNull
  public T[] getExtensions(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance).getExtensions();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#extensions(AreaInstance)}
   */
  @NotNull
  public Stream<T> extensions(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance).extensions();
  }

  @NotNull
  public ExtensionPoint<T> getPoint(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance);
  }

  @Nullable
  public <V extends T> V findExtension(@NotNull Class<V> instanceOf) {
    return getPointImpl(null).findExtension(instanceOf, false, ThreeState.UNSURE);
  }

  @NotNull
  public <V extends T> V findExtensionOrFail(@NotNull Class<V> exactClass) {
    //noinspection ConstantConditions
    return getPointImpl(null).findExtension(exactClass, true, ThreeState.UNSURE);
  }

  @Nullable
  public <V extends T> V findFirstAssignableExtension(@NotNull Class<V> instanceOf) {
    return getPointImpl(null).findExtension(instanceOf, true, ThreeState.NO);
  }

  @NotNull
  public <V extends T> V findExtensionOrFail(@NotNull Class<V> instanceOf, @Nullable AreaInstance areaInstance) {
    //noinspection ConstantConditions
    return getPointImpl(areaInstance).findExtension(instanceOf, true, ThreeState.UNSURE);
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
  public final Iterable<T> getIterable() {
    return getPointImpl(null);
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public void processWithPluginDescriptor(@NotNull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    getPointImpl(null).processWithPluginDescriptor(consumer);
  }

  public void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener, @Nullable Disposable parentDisposable) {
    getPointImpl(null).addExtensionPointListener(listener, false, parentDisposable);
  }
}
