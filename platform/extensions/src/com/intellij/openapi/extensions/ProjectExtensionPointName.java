// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public final class ProjectExtensionPointName<T> extends BaseExtensionPointName {
  public ProjectExtensionPointName(@NotNull String name) {
    super(name);
  }

  @NotNull
  public ExtensionPoint<T> getPoint(@NotNull AreaInstance areaInstance) {
    return Extensions.getArea(areaInstance).getExtensionPoint(getName());
  }

  @NotNull
  public List<T> getExtensions(@NotNull AreaInstance areaInstance) {
    return getPoint(areaInstance).getExtensionList();
  }

  @NotNull
  public Stream<T> extensions(@NotNull AreaInstance areaInstance) {
    return getPoint(areaInstance).extensions();
  }

  @Nullable
  public <V extends T> V findExtension(@NotNull Class<V> instanceOf, @NotNull AreaInstance areaInstance) {
    return ContainerUtil.findInstance(getExtensions(areaInstance), instanceOf);
  }
}
