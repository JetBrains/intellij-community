// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ExtensionPointUtil {
  private ExtensionPointUtil() { }

  public static @NotNull <V extends ClearableLazyValue<?>> V dropLazyValueOnChange(@NotNull V lazyValue,
                                                                                   @NotNull ExtensionPointName<?> extensionPointName,
                                                                                   @Nullable Disposable parentDisposable) {
    extensionPointName.addChangeListener(lazyValue::drop, parentDisposable);
    return lazyValue;
  }

  public static <T> @NotNull Supplier<T> dropLazyValueOnChange(@NotNull SynchronizedClearableLazy<T> lazyValue,
                                                               @NotNull ExtensionPointName<?> extensionPointName,
                                                               @Nullable Disposable parentDisposable) {
    extensionPointName.addChangeListener(lazyValue::drop, parentDisposable);
    return lazyValue;
  }

  @Internal
  public static @NotNull <T> Disposable createExtensionDisposable(@NotNull T extensionObject,
                                                                  @NotNull ExtensionPointName<T> extensionPointName) {
    return createExtensionDisposable(extensionObject, extensionPointName.getPoint());
  }

  @Internal
  public static @NotNull <T> Disposable createExtensionDisposable(@NotNull T extensionObject, @NotNull ExtensionPoint<@NotNull T> extensionPoint) {
    return createExtensionDisposable(extensionObject, extensionPoint, removed -> removed == extensionObject);
  }

  @Internal
  public static @NotNull <T, U> Disposable createExtensionDisposable(@NotNull T extensionObject,
                                                                     @NotNull ExtensionPoint<@NotNull U> extensionPoint,
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
                                                                       @NotNull ExtensionPoint<@NotNull KeyedLazyInstance<T>> extensionPoint) {
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
    ComponentManager manager = ((ExtensionPointImpl<?>)extensionPoint).componentManager;
    Disposer.register(manager, disposable);
    return disposable;
  }
}
