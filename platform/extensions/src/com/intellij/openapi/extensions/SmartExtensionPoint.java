/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public abstract class SmartExtensionPoint<Extension,V> implements ExtensionPointAndAreaListener<Extension> {
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
      if (myCache == null) {
        myExtensionPoint = getExtensionPoint();
        myExtensionPoint.addExtensionPointListener(this);
        myCache = new ArrayList<>(myExplicitExtensions);
        myCache.addAll(ContainerUtil.mapNotNull(myExtensionPoint.getExtensions(), this::getExtension));
      }
      return myCache;
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
  public void areaReplaced(@NotNull final ExtensionsArea area) {
    dropCache();
  }
}
