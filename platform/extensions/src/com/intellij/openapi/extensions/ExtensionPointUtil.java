// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class ExtensionPointUtil {
  public static @NotNull <V extends ClearableLazyValue<?>> V dropLazyValueOnChange(@NotNull V lazyValue,
                                                                                   @NotNull ExtensionPointName<?> extensionPointName,
                                                                                   @Nullable Disposable parentDisposable) {
    extensionPointName.addChangeListener(lazyValue::drop, parentDisposable);
    return lazyValue;
  }

  public static @NotNull <T> Disposable createExtensionDisposable(@NotNull T extensionObject,
                                                                  @NotNull ExtensionPointName<T> extensionPointName) {
    return createExtensionDisposable(extensionObject, extensionPointName.getPoint());
  }

  public static @NotNull <T> Disposable createExtensionDisposable(@NotNull T extensionObject, @NotNull ExtensionPoint<T> extensionPoint) {
    return createExtensionDisposable(extensionObject, extensionPoint, removed -> removed == extensionObject);
  }

  public static @NotNull <T, U> Disposable createExtensionDisposable(@NotNull T extensionObject,
                                                                     @NotNull ExtensionPoint<U> extensionPoint,
                                                                     @NotNull Predicate<? super U> removePredicate) {
    Disposable disposable = createDisposable(extensionObject, extensionPoint);
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<U>() {
      @Override
      public void extensionRemoved(@NotNull U removedExtension, @NotNull PluginDescriptor pluginDescriptor) {
        if (removePredicate.test(removedExtension)) {
          Disposer.dispose(disposable);
        }
      }
    }, false, disposable);
    return disposable;
  }

  public static @NotNull <T> Disposable createKeyedExtensionDisposable(@NotNull T extensionObject,
                                                                       @NotNull ExtensionPoint<KeyedLazyInstance<T>> extensionPoint) {
    Disposable disposable = createDisposable(extensionObject, extensionPoint);
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstance<T>>() {
      @Override
      public void extensionRemoved(@NotNull KeyedLazyInstance<T> removedExtension, @NotNull PluginDescriptor pluginDescriptor) {
        if (extensionObject == removedExtension.getInstance()) {
          Disposer.dispose(disposable);
        }
      }
    }, false, disposable);
    return disposable;
  }

  private static @NotNull <T> Disposable createDisposable(@NotNull T extensionObject, @NotNull ExtensionPoint<?> extensionPoint) {
    Disposable disposable = Disposer.newDisposable("Disposable for [" + extensionObject + "]");
    ComponentManager manager = ((ExtensionPointImpl<?>)extensionPoint).getComponentManager();
    Disposer.register(manager, disposable);
    return disposable;
  }
}
