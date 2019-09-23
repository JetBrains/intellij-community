// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class Extensions {
  private static ExtensionsAreaImpl ourRootArea;

  private Extensions() {
  }

  public static void setRootArea(@NotNull ExtensionsAreaImpl area) {
    ourRootArea = area;
  }

  @TestOnly
  public static void setRootArea(@NotNull ExtensionsAreaImpl area, @NotNull Disposable parentDisposable) {
    ExtensionsAreaImpl oldRootArea = ourRootArea;
    ourRootArea = area;
    Disposer.register(parentDisposable, () -> {
      ourRootArea.notifyAreaReplaced(oldRootArea);
      ourRootArea = oldRootArea;
    });
  }

  /**
   * @return instance containing application-level extensions
   */
  public static ExtensionsArea getRootArea() {
    return ourRootArea;
  }

  /**
   * @deprecated Use {@link AreaInstance#getExtensionArea()}
   */
  @NotNull
  @Deprecated
  public static ExtensionsArea getArea(@Nullable("null means root") AreaInstance areaInstance) {
    return areaInstance == null ? ourRootArea : areaInstance.getExtensionArea();
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensions()}
   */
  @NotNull
  @Deprecated
  public static Object[] getExtensions(@NonNls @NotNull String extensionPointName) {
    return getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensionList()}
   */
  @Deprecated
  @NotNull
  public static <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return extensionPointName.getExtensions();
  }

  /**
   * @deprecated Use {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @Deprecated
  @NotNull
  public static <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName, @Nullable AreaInstance areaInstance) {
    return extensionPointName.getExtensions(areaInstance);
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensions()}
   */
  @Deprecated
  @NotNull
  public static <T> T[] getExtensions(@NotNull String extensionPointName, @Nullable("null means root") AreaInstance areaInstance) {
    return getArea(areaInstance).<T>getExtensionPoint(extensionPointName).getExtensions();
  }

  /**
   * @deprecated Use {@link ExtensionPointName#findExtensionOrFail(Class)}
   */
  @Deprecated
  @NotNull
  public static <T, U extends T> U findExtension(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<U> extClass) {
    return extensionPointName.findExtensionOrFail(extClass);
  }

  /**
   * @deprecated Not needed.
   */
  @SuppressWarnings("unused")
  @Deprecated
  public static void registerAreaClass(@NonNls @NotNull String areaClass, @Nullable @NonNls String parentAreaClass) {
  }
}
