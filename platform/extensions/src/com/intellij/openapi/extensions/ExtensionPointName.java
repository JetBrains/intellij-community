// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.function.*;
import java.util.stream.Stream;

/**
 * Provides access to an <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_extension_points.html">extension point</a>. Instances of this class can be safely stored in static final fields.
 * <p>For project-level and module-level extension points use {@link ProjectExtensionPointName} instead to make it evident that corresponding
 * {@link AreaInstance} must be passed.</p>
 */
public final class ExtensionPointName<T> extends BaseExtensionPointName<T> {
  public ExtensionPointName(@NotNull @NonNls String name) {
    super(name);
  }

  public static @NotNull <T> ExtensionPointName<T> create(@NonNls @NotNull String name) {
    return new ExtensionPointName<>(name);
  }

  /**
   * Prefer to use {@link #getExtensionList()}.
   */
  public T @NotNull [] getExtensions() {
    return getPointImpl(null).getExtensions();
  }

  public @NotNull List<T> getExtensionList() {
    return getPointImpl(null).getExtensionList();
  }

  /**
   * Invokes the given consumer for each extension registered in this extension point. Logs exceptions thrown by the consumer.
   */
  public void forEachExtensionSafe(@NotNull Consumer<? super T> consumer) {
    ExtensionProcessingHelper.forEachExtensionSafe(getPointImpl(null), consumer);
  }

  public @Nullable T findFirstSafe(@NotNull Predicate<? super T> predicate) {
    return ExtensionProcessingHelper.findFirstSafe(predicate, getPointImpl(null));
  }

  public @Nullable <R> R computeSafeIfAny(@NotNull Function<? super T, ? extends R> processor) {
    return ExtensionProcessingHelper.computeSafeIfAny(processor, getPointImpl(null));
  }

  public @NotNull List<T> getExtensionsIfPointIsRegistered() {
    return getExtensionsIfPointIsRegistered(null);
  }

  public @NotNull List<T> getExtensionsIfPointIsRegistered(@Nullable AreaInstance areaInstance) {
    //noinspection deprecation
    ExtensionsArea area = areaInstance == null ? Extensions.getRootArea() : areaInstance.getExtensionArea();
    ExtensionPoint<T> point = area == null ? null : area.getExtensionPointIfRegistered(getName());
    return point == null ? Collections.emptyList() : point.getExtensionList();
  }

  public @NotNull Stream<T> extensions() {
    return getPointImpl(null).extensions();
  }

  public boolean hasAnyExtensions() {
    return getPointImpl(null).size() != 0;
  }

  /**
   * Consider using {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  public @NotNull List<T> getExtensionList(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance).getExtensionList();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  public T @NotNull [] getExtensions(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance).getExtensions();
  }

  /**
   * Consider using {@link ProjectExtensionPointName#extensions(AreaInstance)}
   */
  public @NotNull Stream<T> extensions(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance).extensions();
  }

  /**
   * @deprecated use {@link #getPoint()} to access application-level extensions and {@link ProjectExtensionPointName#getPoint(AreaInstance)}
   * to access project-level and module-level extensions
   */
  @Deprecated
  public @NotNull ExtensionPoint<T> getPoint(@Nullable AreaInstance areaInstance) {
    return getPointImpl(areaInstance);
  }

  public @NotNull ExtensionPoint<T> getPoint() {
    return getPointImpl(null);
  }

  public @Nullable <V extends T> V findExtension(@NotNull Class<V> instanceOf) {
    return getPointImpl(null).findExtension(instanceOf, false, ThreeState.UNSURE);
  }

  public @NotNull <V extends T> V findExtensionOrFail(@NotNull Class<V> exactClass) {
    //noinspection ConstantConditions
    return getPointImpl(null).findExtension(exactClass, true, ThreeState.UNSURE);
  }

  public @Nullable <V extends T> V findFirstAssignableExtension(@NotNull Class<V> instanceOf) {
    return getPointImpl(null).findExtension(instanceOf, true, ThreeState.NO);
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behaviour is not predictable -
   * events will be fired during iteration and probably it will be not expected.
   * <p>
   * Use only for interface extension points, not for bean.
   * <p>
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case stop iteration).
   * <p>
   * Possible use cases:
   * 1. Conditional iteration (no need to create all extensions if iteration will be stopped due to some condition).
   * 2. Iterated only once per application (no need to cache extension list internally).
   */
  @ApiStatus.Internal
  public @NotNull Iterable<T> getIterable() {
    return getPointImpl(null);
  }

  @ApiStatus.Internal
  public void processWithPluginDescriptor(@NotNull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    getPointImpl(null).processWithPluginDescriptor(/* shouldBeSorted = */ true, consumer);
  }

  public void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener, @Nullable Disposable parentDisposable) {
    getPointImpl(null).addExtensionPointListener(listener, false, parentDisposable);
  }

  public void addChangeListener(@NotNull Runnable listener, @Nullable Disposable parentDisposable) {
    getPointImpl(null).addChangeListener(listener, parentDisposable);
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Values with the same key merge into list. Return values by key.
   * <p>
   * To exclude extension from cache, return null key.
   *
   * {@code cacheId} is required because it's dangerous to rely on identity of functional expressions.
   * JLS doesn't specify whether a new instance is produced or some common instance is reused for lambda expressions (see 15.27.4).
   */
  @ApiStatus.Experimental
  public <@NotNull K> @NotNull List<T> getByGroupingKey(@NotNull K key, @NotNull Class<?> cacheId, @NotNull Function<? super @NotNull T, ? extends @Nullable K> keyMapper) {
    return ExtensionProcessingHelper.getByGroupingKey(getPointImpl(null), cacheId, key, keyMapper);
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Return value by key.
   * <p>
   * To exclude extension from cache, return null key.
   */
  @ApiStatus.Experimental
  public <@NotNull K> @Nullable T getByKey(@NotNull K key, @NotNull Class<?> cacheId, @NotNull Function<? super @NotNull T, ? extends @Nullable K> keyMapper) {
    return ExtensionProcessingHelper.getByKey(getPointImpl(null), key, cacheId, keyMapper);
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Return value by key.
   * <p>
   * To exclude extension from cache, return null key.
   */
  @ApiStatus.Experimental
  public <@NotNull K, @NotNull V> @Nullable V getByKey(@NotNull K key,
                                                       @NotNull Class<?> cacheId,
                                                       @NotNull Function<? super @NotNull T, ? extends @Nullable K> keyMapper,
                                                       @NotNull Function<? super @NotNull T, ? extends @Nullable V> valueMapper) {
    return ExtensionProcessingHelper.getByKey(getPointImpl(null), key, cacheId, keyMapper, valueMapper);
  }

  @ApiStatus.Experimental
  public <@NotNull K, @NotNull V> @NotNull V computeIfAbsent(@NotNull K key,
                                                             @NotNull Class<?> cacheId,
                                                             @NotNull Function<? super @NotNull K, ? extends @NotNull V> valueMapper) {
    return ExtensionProcessingHelper.computeIfAbsent(getPointImpl(null), key, cacheId, valueMapper);
  }

  /**
   * Cache some value per extension point.
   */
  @ApiStatus.Experimental
  public <@NotNull V> @NotNull V computeIfAbsent(@NotNull Class<?> cacheId, @NotNull Supplier<? extends @NotNull V> valueMapper) {
    return ExtensionProcessingHelper.computeIfAbsent(getPointImpl(null), cacheId, valueMapper);
  }
}
