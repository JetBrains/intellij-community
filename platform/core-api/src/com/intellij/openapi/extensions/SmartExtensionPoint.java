// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import kotlin.Unit;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static kotlinx.collections.immutable.ExtensionsKt.mutate;
import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;

public abstract class SmartExtensionPoint<T> {
  private volatile PersistentList<T> explicitExtensions = persistentListOf();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private volatile ExtensionPoint<@NotNull T> extensionPoint;
  private volatile PersistentList<T> cache;
  private final ExtensionPointAndAreaListener<T> extensionPointAndAreaListener;

  private final Object lock = new Object();

  protected SmartExtensionPoint() {
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
    return new SmartExtensionPoint<T>() {
      @Override
      protected @NotNull ExtensionPoint<@NotNull T> getExtensionPoint() {
        return Extensions.getRootArea().getExtensionPoint(epName);
      }
    };
  }

  public static <T> SmartExtensionPoint<T> create(ExtensionsArea area, ExtensionPointName<T> epName) {
    return new SmartExtensionPoint<T>() {
      @Override
      protected @NotNull ExtensionPoint<@NotNull T> getExtensionPoint() {
        return area.getExtensionPoint(epName);
      }
    };
  }

  protected abstract @NotNull ExtensionPoint<@NotNull T> getExtensionPoint();

  public final void addExplicitExtension(@NotNull T extension) {
    synchronized (lock) {
      explicitExtensions.add(extension);
      cache = null;
    }
  }

  public final void removeExplicitExtension(@NotNull T extension) {
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
      extensionPoint = getExtensionPoint();
      this.extensionPoint = extensionPoint;
    }

    PersistentList<T> registeredExtensions;
    List<T> collection = extensionPoint.getExtensionList();
    if (collection.isEmpty()) {
      registeredExtensions = persistentListOf();
    }
    else {
      registeredExtensions = mutate(persistentListOf(), mutator -> {
        mutator.addAll(collection);
        return Unit.INSTANCE;
      });
    }

    synchronized (lock) {
      result = cache;
      if (result != null) {
        return result;
      }

      // EP will not add duplicated listener, so, it is safe to not care about is already added
      extensionPoint.addExtensionPointListener(extensionPointAndAreaListener, false, null);
      result = explicitExtensions.addAll(registeredExtensions);
      cache = result;
      return result;
    }
  }
}
