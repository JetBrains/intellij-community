// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public abstract class SmartExtensionPoint<Extension, V> {
  private final Collection<V> myExplicitExtensions;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private volatile ExtensionPoint<Extension> myExtensionPoint;
  private volatile List<V> myCache;
  private final ExtensionPointAndAreaListener<Extension> myExtensionPointAndAreaListener;

  protected SmartExtensionPoint(@NotNull final Collection<V> explicitExtensions) {
    myExplicitExtensions = explicitExtensions;

    myExtensionPointAndAreaListener = new ExtensionPointAdapter<Extension>() {
      @Override
      public void areaReplaced(@NotNull ExtensionsArea oldArea) {
        dropCache();
      }

      @Override
      public void extensionListChanged() {
        dropCache();
      }

      private void dropCache() {
        if (myCache == null) {
          return;
        }

        synchronized (myExplicitExtensions) {
          if (myCache != null) {
            myCache = null;
            ExtensionPoint<Extension> extensionPoint = myExtensionPoint;
            if (extensionPoint != null) {
              extensionPoint.removeExtensionPointListener(this);
              myExtensionPoint = null;
            }
          }
        }
      }
    };
  }

  @NotNull
  protected abstract ExtensionPoint<Extension> getExtensionPoint();

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

  @Nullable
  protected abstract V getExtension(@NotNull final Extension extension);

  @NotNull
  public final List<V> getExtensions() {
    List<V> result = myCache;
    if (result != null) {
      return result;
    }

    // it is ok to call getExtensionPoint several times - call is cheap and implementation is thread-safe
    ExtensionPoint<Extension> extensionPoint = myExtensionPoint;
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
