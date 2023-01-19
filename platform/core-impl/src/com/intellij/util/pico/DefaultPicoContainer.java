// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.pico;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

import java.util.*;
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

  public final @NotNull Collection<ComponentAdapter> getComponentAdapters() {
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

  public final void release() {
    componentKeyToAdapter.clear();
    componentAdapters.clear();
  }

  private @Nullable ComponentAdapter getFromCache(@NotNull Object componentKey) {
    ComponentAdapter adapter = componentKeyToAdapter.get(componentKey);
    if (adapter != null) {
      return adapter;
    }
    return componentKey instanceof Class ? componentKeyToAdapter.get(((Class<?>)componentKey).getName()) : null;
  }

  public final @Nullable ComponentAdapter getComponentAdapterOfType(@NotNull Class<?> componentType) {
    // See http://jira.codehaus.org/secure/ViewIssue.jspa?key=PICO-115
    ComponentAdapter adapterByKey = getComponentAdapter(componentType);
    if (adapterByKey != null) {
      return adapterByKey;
    }

    List<ComponentAdapter> found = getComponentAdaptersOfType(componentType);
    if (found.size() == 1) {
      return found.get(0);
    }
    if (found.isEmpty()) {
      return parent == null ? null : parent.getComponentAdapterOfType(componentType);
    }

    Class<?>[] foundClasses = new Class[found.size()];
    for (int i = 0; i < foundClasses.length; i++) {
      foundClasses[i] = found.get(i).getComponentImplementation();
    }
    throw new AmbiguousComponentResolutionException(componentType, foundClasses);
  }

  public final @NotNull List<ComponentAdapter> getComponentAdaptersOfType(@NotNull Class<?> componentType) {
    if (componentType == String.class) {
      return Collections.emptyList();
    }

    List<ComponentAdapter> result = new ArrayList<>();

    ComponentAdapter cacheHit = componentKeyToAdapter.get(componentType.getName());
    if (cacheHit != null) {
      result.add(cacheHit);
    }

    for (ComponentAdapter adapter : componentKeyToAdapter.values()) {// exclude services
      if (adapter.getComponentKey() instanceof String) {
        continue;
      }

      Class<?> descendant = adapter.getComponentImplementation();
      if (componentType == descendant || componentType.isAssignableFrom(descendant)) {
        result.add(adapter);
      }
    }
    return result;
  }

  public final ComponentAdapter registerComponent(@NotNull ComponentAdapter componentAdapter) {
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
    ComponentAdapter componentAdapter = getComponentAdapterOfType(componentType);
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

  public final DefaultPicoContainer getParent() {
    return parent;
  }

  /**
   * A linked hash set that's copied on write operations.
   */
  private static final class LinkedHashSetWrapper<T> {
    private final Object lock = new Object();
    private volatile Set<T> immutableSet;
    private LinkedHashSet<T> synchronizedSet = new LinkedHashSet<>();

    public void add(@NotNull T element) {
      synchronized (lock) {
        if (!synchronizedSet.contains(element)) {
          copySyncSetIfExposedAsImmutable().add(element);
        }
      }
    }

    private LinkedHashSet<T> copySyncSetIfExposedAsImmutable() {
      if (immutableSet != null) {
        immutableSet = null;
        synchronizedSet = new LinkedHashSet<>(synchronizedSet);
      }
      return synchronizedSet;
    }

    public void remove(@Nullable T element) {
      synchronized (lock) {
        copySyncSetIfExposedAsImmutable().remove(element);
      }
    }

    public void clear() {
      synchronized (lock) {
        if (immutableSet != null) {
          immutableSet = null;
        }
        synchronizedSet = new LinkedHashSet<>();
      }
    }

    public @NotNull Set<T> getImmutableSet() {
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

  public static final class InstanceComponentAdapter implements ComponentAdapter {
    private final Object componentKey;
    private final Object componentInstance;

    public InstanceComponentAdapter(@NotNull Object componentKey, @NotNull Object componentInstance) {
      this.componentKey = componentKey;
      this.componentInstance = componentInstance;
    }

    @Override
    public Object getComponentInstance() {
      return componentInstance;
    }

    @Override
    public Object getComponentKey() {
      return componentKey;
    }

    @Override
    public Class<?> getComponentImplementation() {
      return componentInstance.getClass();
    }

    public String toString() {
      return getClass().getName() + "[" + getComponentKey() + "]";
    }
  }
}
