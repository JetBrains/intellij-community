// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.pico;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class DefaultPicoContainer implements MutablePicoContainer {
  private final DefaultPicoContainer parent;

  private final Map<Object, ComponentAdapter> componentKeyToAdapter = new ConcurrentHashMap<>();
  private final LinkedHashSetWrapper<ComponentAdapter> componentAdapters = new LinkedHashSetWrapper<>();

  public DefaultPicoContainer(@Nullable DefaultPicoContainer parent) {
    this.parent = parent;
  }

  public DefaultPicoContainer() {
    this(null);
  }

  public final @NotNull @Unmodifiable Collection<ComponentAdapter> getComponentAdapters() {
    return componentAdapters.getImmutableSet();
  }

  @Override
  public final @Nullable ComponentAdapter getComponentAdapter(@NotNull Object componentKey) {
    ComponentAdapter adapter = getFromCache(componentKey);
    if (adapter == null && parent != null) {
      return parent.getComponentAdapter(componentKey);
    }
    return adapter;
  }

  private @Nullable ComponentAdapter getFromCache(@NotNull Object componentKey) {
    ComponentAdapter adapter = componentKeyToAdapter.get(componentKey);
    if (adapter != null) {
      return adapter;
    }
    return componentKey instanceof Class ? componentKeyToAdapter.get(((Class<?>)componentKey).getName()) : null;
  }

  final @Nullable ComponentAdapter getComponentAdapterOfType(@NotNull Class<?> componentType, @Nullable Object excludeKey) {
    // See http://jira.codehaus.org/secure/ViewIssue.jspa?key=PICO-115
    ComponentAdapter adapterByKey = getComponentAdapter(componentType);
    if (adapterByKey != null && (excludeKey == null || !excludeKey.equals(adapterByKey.getComponentKey()))) {
      return adapterByKey;
    }

    List<ComponentAdapter> found = getComponentAdaptersOfType(componentType, excludeKey);
    if (found.isEmpty()) {
      return parent == null ? null : parent.getComponentAdapterOfType(componentType, excludeKey);
    }
    if (found.size() == 1) {
      return found.get(0);
    }

    Class<?>[] foundClasses = new Class[found.size()];
    for (int i = 0; i < foundClasses.length; i++) {
      foundClasses[i] = found.get(i).getComponentImplementation();
    }
    throw new AmbiguousComponentResolutionException(componentType, foundClasses);
  }

  private @NotNull @Unmodifiable List<ComponentAdapter> getComponentAdaptersOfType(@NotNull Class<?> componentType,
                                                                                   @Nullable Object excludeKey) {
    if (componentType == String.class) {
      return Collections.emptyList();
    }

    List<ComponentAdapter> result = new ArrayList<>();

    ComponentAdapter cacheHit = componentKeyToAdapter.get(componentType.getName());
    if (cacheHit != null) {
      result.add(cacheHit);
    }

    for (ComponentAdapter adapter : componentKeyToAdapter.values()) {
      // exclude services
      Object componentKey = adapter.getComponentKey();
      if (componentKey instanceof String || excludeKey != null && excludeKey.equals(componentKey)) {
        continue;
      }

      Class<?> descendant = adapter.getComponentImplementation();
      if (componentType == descendant || componentType.isAssignableFrom(descendant)) {
        result.add(adapter);
      }
    }
    return result;
  }

  private @NotNull ComponentAdapter registerComponent(@NotNull ComponentAdapter componentAdapter) {
    if (componentKeyToAdapter.putIfAbsent(componentAdapter.getComponentKey(), componentAdapter) != null) {
      @NotNull String message = "Key " + componentAdapter.getComponentKey() + " duplicated";
      throw new PicoException(message);
    }
    componentAdapters.add(componentAdapter);
    return componentAdapter;
  }

  @Override
  public final @Nullable ComponentAdapter unregisterComponent(@NotNull Object componentKey) {
    ComponentAdapter adapter = componentKeyToAdapter.remove(componentKey);
    if (adapter == null) {
      return null;
    }

    componentAdapters.remove(adapter);
    return adapter;
  }

  @Override
  public @Nullable Object getComponentInstance(@NotNull Object componentKey) {
    ComponentAdapter adapter = getFromCache(componentKey);
    if (adapter != null) {
      return adapter.getComponentInstance();
    }
    return parent == null ? null : parent.getComponentInstance(componentKey);
  }

  public final @Nullable <T> T getService(@NotNull Class<T> serviceClass) {
    ComponentAdapter adapter = componentKeyToAdapter.get(serviceClass.getName());
    if (adapter == null) {
      return null;
    }

    //noinspection unchecked
    return (T)adapter.getComponentInstance();
  }

  @Override
  public final @Nullable Object getComponentInstanceOfType(@NotNull Class<?> componentType) {
    ComponentAdapter componentAdapter = getComponentAdapterOfType(componentType, null);
    return componentAdapter == null ? null : getInstance(componentAdapter);
  }

  private @Nullable Object getInstance(@NotNull ComponentAdapter componentAdapter) {
    if (componentAdapters.getImmutableSet().contains(componentAdapter)) {
      return componentAdapter.getComponentInstance();
    }
    if (parent != null) {
      return parent.getComponentInstance(componentAdapter.getComponentKey());
    }
    return null;
  }

  @Override
  public final ComponentAdapter registerComponentInstance(@NotNull Object componentKey, @NotNull Object componentInstance) {
    return registerComponent(new InstanceComponentAdapter(componentKey, componentInstance));
  }

  @Override
  public final ComponentAdapter registerComponentImplementation(@NotNull Object componentKey, @NotNull Class<?> componentImplementation) {
    //noinspection deprecation
    return registerComponent(new CachingConstructorInjectionComponentAdapter(this, componentKey, componentImplementation));
  }

  /**
   * A linked hash set that's copied on write operations.
   */
  private static final class LinkedHashSetWrapper<T> {
    private final Object lock = new Object();
    private volatile @Unmodifiable Set<T> immutableSet;
    private Set<T> synchronizedSet = new LinkedHashSet<>();

    void add(@NotNull T element) {
      synchronized (lock) {
        if (!synchronizedSet.contains(element)) {
          copySyncSetIfExposedAsImmutable().add(element);
        }
      }
    }

    private Set<T> copySyncSetIfExposedAsImmutable() {
      if (immutableSet != null) {
        immutableSet = null;
        synchronizedSet = new LinkedHashSet<>(synchronizedSet);
      }
      return synchronizedSet;
    }

    void remove(@Nullable T element) {
      synchronized (lock) {
        copySyncSetIfExposedAsImmutable().remove(element);
      }
    }

    @NotNull @Unmodifiable Set<T> getImmutableSet() {
      Set<T> result = immutableSet;
      if (result == null) {
        synchronized (lock) {
          result = immutableSet;
          if (result == null) {
            // Expose the same set as immutable. It should be never modified again. Next add/remove operations will copy synchronizedSet
            result = Collections.unmodifiableSet(synchronizedSet);
            immutableSet = result;
          }
        }
      }

      return result;
    }
  }

  @Override
  public final String toString() {
    DefaultPicoContainer parent = this.parent;
    return "DefaultPicoContainer" + (parent == null ? " (root)" : " (parent=" + parent + ")");
  }

  static final class InstanceComponentAdapter implements ComponentAdapter {
    private final @NotNull Object componentKey;
    private final @NotNull Object componentInstance;

    InstanceComponentAdapter(@NotNull Object componentKey, @NotNull Object componentInstance) {
      this.componentKey = componentKey;
      this.componentInstance = componentInstance;
    }

    @Override
    public @NotNull Object getComponentInstance() {
      return componentInstance;
    }

    @Override
    public @NotNull Object getComponentKey() {
      return componentKey;
    }

    @Override
    public @NotNull Class<?> getComponentImplementation() {
      return componentInstance.getClass();
    }

    @Override
    public String toString() {
      return getClass().getName() + "[" + getComponentKey() + "]";
    }
  }
}
