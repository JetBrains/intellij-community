// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.application.ApplicationManager;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;
import static kotlinx.collections.immutable.ExtensionsKt.toPersistentList;

@ApiStatus.Internal
public final class SmartExtensionPoint<T> {
  private final @NotNull Supplier<? extends ExtensionPoint<@NotNull T>> extensionPointSupplier;
  private volatile PersistentList<T> explicitExtensions = persistentListOf();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private volatile ExtensionPoint<@NotNull T> extensionPoint;
  private volatile PersistentList<T> cache;
  private final ExtensionPointAndAreaListener<T> extensionPointAndAreaListener;

  private final Object lock = new Object();

  public SmartExtensionPoint(@NotNull Supplier<? extends ExtensionPoint<@NotNull T>> extensionPointSupplier) {
    this.extensionPointSupplier = extensionPointSupplier;
    extensionPointAndAreaListener = new ExtensionPointAndAreaListener<T>() {
      @Override
      public void areaReplaced(@NotNull ExtensionsArea oldArea) {
        dropCache();
      }

      @Override
      public void extensionAdded(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
        dropCache();
      }

      @Override
      public void extensionRemoved(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
        dropCache();
      }

      private void dropCache() {
        if (cache == null) {
          return;
        }

        synchronized (lock) {
          if (cache != null) {
            cache = null;
            ExtensionPoint<@NotNull T> extensionPoint = SmartExtensionPoint.this.extensionPoint;
            if (extensionPoint != null) {
              extensionPoint.removeExtensionPointListener(this);
              SmartExtensionPoint.this.extensionPoint = null;
            }
          }
        }
      }
    };
  }

  public static <T> SmartExtensionPoint<T> create(ExtensionPointName<T> epName) {
    return new SmartExtensionPoint<>(() -> ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(epName));
  }

  public static <T> SmartExtensionPoint<T> create(@NotNull ExtensionsArea area, @NotNull ExtensionPointName<T> epName) {
    return new SmartExtensionPoint<>(() -> area.getExtensionPoint(epName));
  }

  public void addExplicitExtension(@NotNull T extension) {
    synchronized (lock) {
      explicitExtensions.add(extension);
      cache = null;
    }
  }

  public void removeExplicitExtension(@NotNull T extension) {
    synchronized (lock) {
      explicitExtensions = explicitExtensions.remove(extension);
      cache = null;
    }
  }

  public final @NotNull List<T> getExtensions() {
    PersistentList<T> result = cache;
    if (result != null) {
      return result;
    }

    // it is ok to call getExtensionPoint several times - call is cheap, and implementation is thread-safe
    ExtensionPoint<T> extensionPoint = this.extensionPoint;
    if (extensionPoint == null) {
      extensionPoint = extensionPointSupplier.get();
      this.extensionPoint = extensionPoint;
    }

    PersistentList<T> extensions = toPersistentList(extensionPoint.getExtensionList());
    synchronized (lock) {
      result = cache;
      if (result != null) {
        return result;
      }

      // EP will not add duplicated listener, so, it is safe to not care about is already added
      extensionPoint.addExtensionPointListener(extensionPointAndAreaListener, false, null);
      result = explicitExtensions.addAll(extensions);
      cache = result;
      return result;
    }
  }
}
