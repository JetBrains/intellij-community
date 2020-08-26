// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Provides access to a project-level or module-level extension point. Since extensions are supposed to be stateless, storing different
 * instances of an extension for each project or module just waste the memory and complicates code, so <strong>it's strongly recommended not
 * to introduce new project-level and module-level extension points</strong>. If you need to have {@link com.intellij.openapi.project.Project Project}
 * or {@link com.intellij.openapi.module.Module Module} instance in some extension's method, just pass it as a parameter and use the default
 * application-level extension point.
 *
 * <p>Instances of this class can be safely stored in static final fields.</p>
 */
public final class ProjectExtensionPointName<T> extends BaseExtensionPointName<T> {
  public ProjectExtensionPointName(@NotNull @NonNls String name) {
    super(name);
  }

  public @NotNull ExtensionPoint<T> getPoint(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance);
  }

  public @NotNull List<T> getExtensions(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).getExtensionList();
  }

  public @NotNull Stream<T> extensions(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).extensions();
  }

  public @Nullable <V extends T> V findExtension(@NotNull Class<V> instanceOf, @NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).findExtension(instanceOf, false, ThreeState.UNSURE);
  }

  public @NotNull <V extends T> V findExtensionOrFail(@NotNull Class<V> instanceOf, @NotNull AreaInstance areaInstance) {
    //noinspection ConstantConditions
    return getPointImpl(areaInstance).findExtension(instanceOf, true, ThreeState.UNSURE);
  }

  public boolean hasAnyExtensions(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).size() != 0;
  }

  public @Nullable T findFirstSafe(@NotNull AreaInstance areaInstance, @NotNull Predicate<? super T> predicate) {
    return ExtensionProcessingHelper.findFirstSafe(predicate, getPointImpl(areaInstance));
  }

  public @Nullable <R> R computeSafeIfAny(@NotNull AreaInstance areaInstance, @NotNull Function<? super T, ? extends R> processor) {
    return ExtensionProcessingHelper.computeSafeIfAny(processor, getPointImpl(areaInstance));
  }


  public void addExtensionPointListener(@NotNull AreaInstance areaInstance,
                                        @NotNull ExtensionPointListener<T> listener,
                                        @Nullable Disposable parentDisposable) {
    getPointImpl(areaInstance).addExtensionPointListener(listener, false, parentDisposable);
  }

  public void addChangeListener(@NotNull AreaInstance areaInstance, @NotNull Runnable listener, @Nullable Disposable parentDisposable) {
    getPointImpl(areaInstance).addChangeListener(listener, parentDisposable);
  }

  public void processWithPluginDescriptor(@NotNull AreaInstance areaInstance, @NotNull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    getPointImpl(areaInstance).processWithPluginDescriptor(true, consumer);
  }

  @ApiStatus.Experimental
  public final @NotNull Iterable<T> getIterable(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance);
  }
}
