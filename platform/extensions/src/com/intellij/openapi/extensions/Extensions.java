// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class Extensions {
  private static ExtensionsAreaImpl rootArea;

  private Extensions() {
  }

  @Internal
  public static void setRootArea(@NotNull ExtensionsAreaImpl area) {
    rootArea = area;
  }

  @Internal
  @TestOnly
  public static void setRootArea(@NotNull ExtensionsAreaImpl area, @NotNull Disposable parentDisposable) {
    ExtensionsAreaImpl oldRootArea = rootArea;
    rootArea = area;
    Disposer.register(parentDisposable, () -> {
      rootArea.notifyAreaReplaced(oldRootArea);
      rootArea = oldRootArea;
    });
  }

  /**
   * @deprecated Use {@link ComponentManager#getExtensionArea()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static ExtensionsArea getRootArea() {
    return rootArea;
  }

  /**
   * @deprecated Use {@link AreaInstance#getExtensionArea()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull ExtensionsArea getArea(@Nullable("null means root") AreaInstance areaInstance) {
    return areaInstance == null ? rootArea : areaInstance.getExtensionArea();
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensionList()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static <T> T @NotNull [] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return extensionPointName.getExtensions();
  }

  /**
   * @deprecated Use {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static <T> T @NotNull [] getExtensions(@NotNull ExtensionPointName<T> extensionPointName, @Nullable AreaInstance areaInstance) {
    return extensionPointName.getExtensions(areaInstance);
  }

  /**
   * @deprecated Use {@link ExtensionPointName#findExtensionOrFail(Class)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull <T, U extends T> U findExtension(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<U> extClass) {
    return extensionPointName.findExtensionOrFail(extClass);
  }
}
