// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class SmartExtensionPoint<Extension, V> {
  private final Collection<V> myExplicitExtensions;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private volatile ExtensionPoint<@NotNull Extension> myExtensionPoint;
  private volatile List<V> myCache;
  private final ExtensionPointAndAreaListener<Extension> myExtensionPointAndAreaListener;

  protected SmartExtensionPoint(@NotNull Collection<V> explicitExtensions) {
    myExplicitExtensions = explicitExtensions;

    myExtensionPointAndAreaListener = new ExtensionPointAndAreaListener<Extension>() {
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
        if (myCache == null) {
          return;
        }

        synchronized (myExplicitExtensions) {
          if (myCache != null) {
            myCache = null;
            ExtensionPoint<@NotNull Extension> extensionPoint = myExtensionPoint;
            if (extensionPoint != null) {
              extensionPoint.removeExtensionPointListener(this);
              myExtensionPoint = null;
            }
          }
        }
      }
    };
  }

  protected abstract @NotNull ExtensionPoint<@NotNull Extension> getExtensionPoint();

  public final void addExplicitExtension(@NotNull V extension) {
    synchronized (myExplicitExtensions) {
      myExplicitExtensions.add(extension);
      myCache = null;
    }
  }

  public final void removeExplicitExtension(@NotNull V extension) {
    synchronized (myExplicitExtensions) {
      myExplicitExtensions.remove(extension);
      myCache = null;
    }
  }

  protected abstract @Nullable V getExtension(final @NotNull Extension extension);

  public final @NotNull List<V> getExtensions() {
    List<V> result = myCache;
    if (result != null) {
      return result;
    }

    // it is ok to call getExtensionPoint several times - call is cheap and implementation is thread-safe
    ExtensionPoint<@NotNull Extension> extensionPoint = myExtensionPoint;
    if (extensionPoint == null) {
      extensionPoint = getExtensionPoint();
      myExtensionPoint = extensionPoint;
    }

    List<V> registeredExtensions = ContainerUtil.mapNotNull(extensionPoint.getExtensionList(), this::getExtension);
    synchronized (myExplicitExtensions) {
      result = myCache;
      if (result != null) {
        return result;
      }

      // EP will not add duplicated listener, so, it is safe to not care about is already added
      extensionPoint.addExtensionPointListener(myExtensionPointAndAreaListener, false, null);
      result = new ArrayList<>(myExplicitExtensions.size() + registeredExtensions.size());
      result.addAll(myExplicitExtensions);
      result.addAll(registeredExtensions);
      myCache = result;
      return result;
    }
  }
}
