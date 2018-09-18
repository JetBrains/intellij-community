// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public abstract class SmartExtensionPoint<Extension, V> implements ExtensionPointAndAreaListener<Extension> {
  private final Collection<V> myExplicitExtensions;
  private ExtensionPoint<Extension> myExtensionPoint;
  private List<V> myCache;

  protected SmartExtensionPoint(@NotNull final Collection<V> explicitExtensions) {
    myExplicitExtensions = explicitExtensions;
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
    synchronized (myExplicitExtensions) {
      List<V> result = myCache;
      if (result == null) {
        myExtensionPoint = getExtensionPoint();
        // EP will not add duplicated listener, so, it is safe to not care about is already added
        myExtensionPoint.addExtensionPointListener(this);

        List<V> registeredExtensions = ContainerUtil.mapNotNull(myExtensionPoint.getExtensions(), this::getExtension);
        result = new ArrayList<>(myExplicitExtensions.size() + registeredExtensions.size());
        result.addAll(myExplicitExtensions);
        result.addAll(registeredExtensions);
        myCache = result;
      }
      return result;
    }
  }

  @Override
  public final void extensionAdded(@NotNull final Extension extension, @Nullable final PluginDescriptor pluginDescriptor) {
    dropCache();
  }

  public final void dropCache() {
    synchronized (myExplicitExtensions) {
      if (myCache != null) {
        myCache = null;
        myExtensionPoint.removeExtensionPointListener(this);
        myExtensionPoint = null;
      }
    }
  }

  @Override
  public final void extensionRemoved(@NotNull final Extension extension, @Nullable final PluginDescriptor pluginDescriptor) {
    dropCache();
  }

  @Override
  public final void areaReplaced(@NotNull final ExtensionsArea area) {
    dropCache();
  }
}
