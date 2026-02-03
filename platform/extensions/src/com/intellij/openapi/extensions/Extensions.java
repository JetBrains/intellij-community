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

import java.util.function.Supplier;

public final class Extensions {
  @Nullable
  private static Supplier<ExtensionsAreaImpl> rootSupplier = null;

  private static ExtensionsAreaImpl staticRootArea;

  private Extensions() {
  }

  @Internal
  public static void setRootArea(@NotNull ExtensionsAreaImpl area) {
    staticRootArea = area;
  }

  @Internal
  public static void setRootAreaSupplier(@NotNull Supplier<ExtensionsAreaImpl> supplier) {
    rootSupplier = supplier;
  }

  @Internal
  @TestOnly
  public static void setRootArea(@NotNull ExtensionsAreaImpl area, @NotNull Disposable parentDisposable) {
    ExtensionsAreaImpl oldRootArea = staticRootArea;
    staticRootArea = area;
    if (oldRootArea != null) {
      oldRootArea.notifyAreaReplaced(area);
    }
    Disposer.register(parentDisposable, () -> {
      staticRootArea.notifyAreaReplaced(oldRootArea);
      staticRootArea = oldRootArea;
    });
  }

  /**
   * @deprecated Use {@link ComponentManager#getExtensionArea()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static ExtensionsArea getRootArea() {
    if (rootSupplier != null) {
      return rootSupplier.get();
    }
    else {
      return staticRootArea;
    }
  }

  /**
   * @deprecated Use {@link AreaInstance#getExtensionArea()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull ExtensionsArea getArea(@Nullable("null means root") AreaInstance areaInstance) {
    return areaInstance == null ? getRootArea() : areaInstance.getExtensionArea();
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
