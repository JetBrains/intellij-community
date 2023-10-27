// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import kotlin.Unit;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static kotlinx.collections.immutable.ExtensionsKt.*;

public abstract class SmartExtensionPoint<Extension, V> {
  private final PersistentList<V> explicitExtensions;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private volatile ExtensionPoint<@NotNull Extension> extensionPoint;
  private volatile PersistentList<V> cache;
  private final ExtensionPointAndAreaListener<Extension> extensionPointAndAreaListener;

  protected SmartExtensionPoint(@NotNull Collection<V> explicitExtensions) {
    this.explicitExtensions = toPersistentList(explicitExtensions);

    extensionPointAndAreaListener = new ExtensionPointAndAreaListener<Extension>() {
      @Override
      public void areaReplaced(@NotNull ExtensionsArea oldArea) {
        dropCache();
      }

      @Override
      public void extensionAdded(@NotNull Extension extension, @NotNull PluginDescriptor pluginDescriptor) {
        dropCache();
      }

      @Override
      public void extensionRemoved(@NotNull Extension extension, @NotNull PluginDescriptor pluginDescriptor) {
        dropCache();
      }

      private void dropCache() {
        if (cache == null) {
          return;
        }

        synchronized (SmartExtensionPoint.this.explicitExtensions) {
          if (cache != null) {
            cache = null;
            ExtensionPoint<@NotNull Extension> extensionPoint = SmartExtensionPoint.this.extensionPoint;
            if (extensionPoint != null) {
              extensionPoint.removeExtensionPointListener(this);
              SmartExtensionPoint.this.extensionPoint = null;
            }
          }
        }
      }
    };
  }

  protected abstract @NotNull ExtensionPoint<@NotNull Extension> getExtensionPoint();

  public final void addExplicitExtension(@NotNull V extension) {
    synchronized (explicitExtensions) {
      explicitExtensions.add(extension);
      cache = null;
    }
  }

  public final void removeExplicitExtension(@NotNull V extension) {
    synchronized (explicitExtensions) {
      explicitExtensions.remove(extension);
      cache = null;
    }
  }

  protected abstract @Nullable V getExtension(final @NotNull Extension extension);

  public final @NotNull List<V> getExtensions() {
    PersistentList<V> result = cache;
    if (result != null) {
      return result;
    }

    // it is ok to call getExtensionPoint several times - call is cheap, and implementation is thread-safe
    ExtensionPoint<Extension> extensionPoint = this.extensionPoint;
    if (extensionPoint == null) {
      extensionPoint = getExtensionPoint();
      this.extensionPoint = extensionPoint;
    }

    PersistentList<V> registeredExtensions;
    List<? extends Extension> collection = extensionPoint.getExtensionList();
    if (collection.isEmpty()) {
      registeredExtensions = persistentListOf();
    }
    else {
      registeredExtensions = mutate(persistentListOf(), mutator -> {
        for (Extension t : collection) {
          V o = getExtension(t);
          if (o != null) {
            mutator.add(o);
          }
        }
        return Unit.INSTANCE;
      });
    }

    synchronized (explicitExtensions) {
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
