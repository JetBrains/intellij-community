// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtensionPointUtil {
  @NotNull
  public static <V extends ClearableLazyValue<?>> V dropLazyValueOnChange(@NotNull V lazyValue,
                                                                          @NotNull ExtensionPointName<?> extensionPointName,
                                                                          @Nullable Disposable parentDisposable) {
    return dropLazyValueOnChange(lazyValue, extensionPointName.getPoint(null), parentDisposable);
  }

  @NotNull
  public static <V extends ClearableLazyValue<?>> V dropLazyValueOnChange(@NotNull V lazyValue,
                                                                          @NotNull ExtensionPoint<?> extensionPoint,
                                                                          @Nullable Disposable parentDisposable) {
    extensionPoint.addExtensionPointListener(lazyValue::drop, false, parentDisposable);
    return lazyValue;
  }

  public static <T> Disposable createExtensionDisposable(@NotNull T extensionObject,
                                                         @NotNull ExtensionPointName<T> extensionPointName) {
    return createExtensionDisposable(extensionObject, extensionPointName.getPoint(null));
  }

  public static <T> Disposable createExtensionDisposable(@NotNull T extensionObject,
                                                         @NotNull ExtensionPoint<T> extensionPoint) {
    Disposable disposable = createDisposable(extensionObject, extensionPoint);
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<T>() {
      @Override
      public void extensionRemoved(@NotNull T removedExtension, @NotNull PluginDescriptor pluginDescriptor) {
        if (extensionObject == removedExtension) {
          Disposer.dispose(disposable);
        }
      }
    }, false, disposable);
    return disposable;
  }

  public static <T> Disposable createKeyedExtensionDisposable(@NotNull T extensionObject,
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

  @NotNull
  private static <T> Disposable createDisposable(@NotNull T extensionObject,
                                                 @NotNull ExtensionPoint extensionPoint) {
    Disposable disposable = Disposer.newDisposable("Disposable for [" + extensionObject.toString() + "]");
    ComponentManager manager = ((ExtensionPointImpl)extensionPoint).getComponentManager();
    Disposer.register(manager, disposable);
    return disposable;
  }
}
