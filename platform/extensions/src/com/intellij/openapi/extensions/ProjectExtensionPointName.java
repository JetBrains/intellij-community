// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ProjectExtensionPointName<T> extends BaseExtensionPointName<T> {
  public ProjectExtensionPointName(@NotNull String name) {
    super(name);
  }

  @NotNull
  public ExtensionPoint<T> getPoint(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance);
  }

  @NotNull
  public List<T> getExtensions(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).getExtensionList();
  }

  @NotNull
  public Stream<T> extensions(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).extensions();
  }

  @Nullable
  public <V extends T> V findExtension(@NotNull Class<V> instanceOf, @NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).findExtension(instanceOf, false, ThreeState.UNSURE);
  }

  @NotNull
  public <V extends T> V findExtensionOrFail(@NotNull Class<V> instanceOf, @NotNull AreaInstance areaInstance) {
    //noinspection ConstantConditions
    return getPointImpl(areaInstance).findExtension(instanceOf, true, ThreeState.UNSURE);
  }

  public boolean hasAnyExtensions(@NotNull AreaInstance areaInstance) {
    return getPointImpl(areaInstance).hasAnyExtensions();
  }

  @Nullable
  public T findFirstSafe(@NotNull AreaInstance areaInstance, @NotNull Predicate<? super T> predicate) {
    return ExtensionProcessingHelper.findFirstSafe(predicate, getPointImpl(areaInstance));
  }

  @Nullable
  public <R> R computeSafeIfAny(@NotNull AreaInstance areaInstance, @NotNull Function<T, R> processor) {
    return ExtensionProcessingHelper.computeSafeIfAny(processor, getPointImpl(areaInstance));
  }
}
