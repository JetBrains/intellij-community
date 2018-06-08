// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.pico;

import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultPicoContainer implements AreaPicoContainer {
  static final DelegatingComponentMonitor DEFAULT_DELEGATING_COMPONENT_MONITOR = new DelegatingComponentMonitor();
  static final DefaultLifecycleStrategy DEFAULT_LIFECYCLE_STRATEGY = new DefaultLifecycleStrategy(DEFAULT_DELEGATING_COMPONENT_MONITOR);
  private final PicoContainer parent;
  private final Set<PicoContainer> children = new THashSet<>();

  private final Map<Object, ComponentAdapter> componentKeyToAdapterCache = ContainerUtil.newConcurrentMap();
  private final LinkedHashSetWrapper<ComponentAdapter> componentAdapters = new LinkedHashSetWrapper<>();
  private final Map<String, ComponentAdapter> classNameToAdapter = ContainerUtil.newConcurrentMap();
  private final AtomicReference<FList<ComponentAdapter>> nonAssignableComponentAdapters = new AtomicReference<>(FList.emptyList());

  public DefaultPicoContainer(@Nullable PicoContainer parent) {
    this.parent = parent == null ? null : ImmutablePicoContainerProxyFactory.newProxyInstance(parent);
  }

  public DefaultPicoContainer() {
    this(null);
  }

  @Override
  public Collection<ComponentAdapter> getComponentAdapters() {
    return componentAdapters.getImmutableSet();
  }

  private void appendNonAssignableAdaptersOfType(@NotNull Class componentType, @NotNull List<ComponentAdapter> result) {
    List<ComponentAdapter> comp = new ArrayList<>();
    for (final ComponentAdapter componentAdapter : nonAssignableComponentAdapters.get()) {
      if (ReflectionUtil.isAssignable(componentType, componentAdapter.getComponentImplementation())) {
        comp.add(componentAdapter);
      }
    }
    for (int i = comp.size() - 1; i >= 0; i--) {
      result.add(comp.get(i));
    }
  }

  @Override
  @Nullable
  public final ComponentAdapter getComponentAdapter(Object componentKey) {
    ComponentAdapter adapter = getFromCache(componentKey);
    if (adapter == null && parent != null) {
      return parent.getComponentAdapter(componentKey);
    }
    return adapter;
  }

  @Nullable
  private ComponentAdapter getFromCache(final Object componentKey) {
    ComponentAdapter adapter = componentKeyToAdapterCache.get(componentKey);
    if (adapter != null) {
      return adapter;
    }

    if (componentKey instanceof Class) {
      return componentKeyToAdapterCache.get(((Class)componentKey).getName());
    }

    return null;
  }

  @Override
  @Nullable
  public ComponentAdapter getComponentAdapterOfType(@NotNull Class componentType) {
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

    Class[] foundClasses = new Class[found.size()];
    for (int i = 0; i < foundClasses.length; i++) {
      foundClasses[i] = found.get(i).getComponentImplementation();
    }
    throw new AmbiguousComponentResolutionException(componentType, foundClasses);
  }

  @Override
  public List<ComponentAdapter> getComponentAdaptersOfType(final Class componentType) {
    if (componentType == null || componentType == String.class) {
      return Collections.emptyList();
    }

    List<ComponentAdapter> result = new SmartList<>();

    final ComponentAdapter cacheHit = classNameToAdapter.get(componentType.getName());
    if (cacheHit != null) {
      result.add(cacheHit);
    }

    appendNonAssignableAdaptersOfType(componentType, result);
    return result;
  }

  @Override
  public ComponentAdapter registerComponent(@NotNull ComponentAdapter componentAdapter) {
    Object componentKey = componentAdapter.getComponentKey();
    if (componentKeyToAdapterCache.containsKey(componentKey)) {
      throw new DuplicateComponentKeyRegistrationException(componentKey);
    }

    if (componentAdapter instanceof AssignableToComponentAdapter) {
      String classKey = ((AssignableToComponentAdapter)componentAdapter).getAssignableToClassName();
      classNameToAdapter.put(classKey, componentAdapter);
    }
    else {
      do {
        FList<ComponentAdapter> oldList = nonAssignableComponentAdapters.get();
        FList<ComponentAdapter> newList = oldList.prepend(componentAdapter);
        if (nonAssignableComponentAdapters.compareAndSet(oldList, newList)) {
          break;
        }
      }
      while (true);
    }

    componentAdapters.add(componentAdapter);

    componentKeyToAdapterCache.put(componentKey, componentAdapter);
    return componentAdapter;
  }

  @Override
  public ComponentAdapter unregisterComponent(@NotNull Object componentKey) {
    ComponentAdapter adapter = componentKeyToAdapterCache.remove(componentKey);
    componentAdapters.remove(adapter);
    if (adapter instanceof AssignableToComponentAdapter) {
      classNameToAdapter.remove(((AssignableToComponentAdapter)adapter).getAssignableToClassName());
    }
    else {
      do {
        FList<ComponentAdapter> oldList = nonAssignableComponentAdapters.get();
        FList<ComponentAdapter> newList = oldList.without(adapter);
        if (nonAssignableComponentAdapters.compareAndSet(oldList, newList)) {
          break;
        }
      }
      while (true);
    }
    return adapter;
  }

  @Override
  public List getComponentInstances() {
    return getComponentInstancesOfType(Object.class);
  }

  @Override
  public List<Object> getComponentInstancesOfType(@Nullable Class componentType) {
    if (componentType == null) {
      return Collections.emptyList();
    }

    List<Object> result = new ArrayList<>();
    for (ComponentAdapter componentAdapter : getComponentAdapters()) {
      if (ReflectionUtil.isAssignable(componentType, componentAdapter.getComponentImplementation())) {
        // may be null in the case of the "implicit" adapter representing "this".
        ContainerUtil.addIfNotNull(result, getInstance(componentAdapter));
      }
    }
    return result;
  }

  public interface LazyComponentAdapter {
    boolean isComponentInstantiated();
  }

  @Nullable
  public <T> T getComponentInstanceIfInstantiated(@NotNull String componentKey) {
    ComponentAdapter adapter = getFromCache(componentKey);
    return getComponentInstanceIfInstantiated(componentKey, adapter);
  }

  @NotNull
  public <T> List<T> getInstantiatedComponents(@NotNull Class<T> componentType) {
    List<T> result = null;
    for (Map.Entry<Object, ComponentAdapter> entry : componentKeyToAdapterCache.entrySet()) {
      ComponentAdapter adapter = entry.getValue();
      if (ReflectionUtil.isAssignable(componentType, adapter.getComponentImplementation())) {
        Object component = getComponentInstanceIfInstantiated(entry.getKey(), entry.getValue());
        if (component != null && componentType.isInstance(component)) {
          if (result == null) {
            result = new ArrayList<>();
          }
          result.add((T)component);
        }
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  @Nullable
  public <T> T getComponentInstanceIfInstantiated(@NotNull Object componentKey, ComponentAdapter adapter) {
    if (!(adapter instanceof LazyComponentAdapter)) {
      //noinspection unchecked
      return (T)getComponentInstance(componentKey);
    }

    if (((LazyComponentAdapter)adapter).isComponentInstantiated()) {
      //noinspection unchecked
      return (T)getLocalInstance(adapter);
    }
    else {
      return null;
    }
  }

  @Override
  @Nullable
  public Object getComponentInstance(Object componentKey) {
    ComponentAdapter adapter = getFromCache(componentKey);
    if (adapter != null) {
      return getLocalInstance(adapter);
    }
    if (parent != null) {
      adapter = parent.getComponentAdapter(componentKey);
      if (adapter != null) {
        return parent.getComponentInstance(adapter.getComponentKey());
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Object getComponentInstanceOfType(Class componentType) {
    final ComponentAdapter componentAdapter = getComponentAdapterOfType(componentType);
    return componentAdapter == null ? null : getInstance(componentAdapter);
  }

  @Nullable
  private Object getInstance(@NotNull ComponentAdapter componentAdapter) {
    if (getComponentAdapters().contains(componentAdapter)) {
      return getLocalInstance(componentAdapter);
    }
    if (parent != null) {
      return parent.getComponentInstance(componentAdapter.getComponentKey());
    }

    return null;
  }

  private Object getLocalInstance(@NotNull ComponentAdapter componentAdapter) {
    PicoException firstLevelException;
    try {
      return componentAdapter.getComponentInstance(this);
    }
    catch (PicoInitializationException | PicoIntrospectionException e) {
      firstLevelException = e;
    }

    if (parent != null) {
      Object instance = parent.getComponentInstance(componentAdapter.getComponentKey());
      if (instance != null) {
        return instance;
      }
    }

    throw firstLevelException;
  }

  @Override
  @Nullable
  public ComponentAdapter unregisterComponentByInstance(@NotNull Object componentInstance) {
    for (ComponentAdapter adapter : getComponentAdapters()) {
      Object o = getInstance(adapter);
      if (o != null && o.equals(componentInstance)) {
        return unregisterComponent(adapter.getComponentKey());
      }
    }
    return null;
  }

  @Override
  public void verify() {
    new VerifyingVisitor().traverse(this);
  }

  @Override
  public void start() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stop() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MutablePicoContainer makeChildContainer() {
    DefaultPicoContainer pc = new DefaultPicoContainer(this);
    addChildContainer(pc);
    return pc;
  }

  @Override
  public boolean addChildContainer(@NotNull PicoContainer child) {
    return children.add(child);
  }

  @Override
  public boolean removeChildContainer(@NotNull PicoContainer child) {
    return children.remove(child);
  }

  @Override
  public void accept(PicoVisitor visitor) {
    visitor.visitContainer(this);

    for (ComponentAdapter adapter : getComponentAdapters()) {
      adapter.accept(visitor);
    }
    for (PicoContainer child : new SmartList<>(children)) {
      child.accept(visitor);
    }
  }

  @Override
  public ComponentAdapter registerComponentInstance(@NotNull Object component) {
    return registerComponentInstance(component.getClass(), component);
  }

  @Override
  public ComponentAdapter registerComponentInstance(@NotNull Object componentKey, @NotNull Object componentInstance) {
    return registerComponent(new InstanceComponentAdapter(componentKey, componentInstance, DEFAULT_LIFECYCLE_STRATEGY));
  }

  @Override
  public ComponentAdapter registerComponentImplementation(@NotNull Class componentImplementation) {
    return registerComponentImplementation(componentImplementation, componentImplementation);
  }

  @Override
  public ComponentAdapter registerComponentImplementation(@NotNull Object componentKey, @NotNull Class componentImplementation) {
    return registerComponentImplementation(componentKey, componentImplementation, null);
  }

  @Override
  public ComponentAdapter registerComponentImplementation(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters) {
    ComponentAdapter componentAdapter = new CachingConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, true);
    return registerComponent(componentAdapter);
  }

  @Override
  public PicoContainer getParent() {
    return parent;
  }

  /**
   * A linked hash set that's copied on write operations.
   * @param <T>
   */
  private static class LinkedHashSetWrapper<T> {
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

    @NotNull
    public Set<T> getImmutableSet() {
      Set<T> res = immutableSet;
      if (res == null) {
        synchronized (lock) {
          res = immutableSet;
          if (res == null) {
            // Expose the same set as immutable. It should be never modified again. Next add/remove operations will copy synchronizedSet
            immutableSet = res = Collections.unmodifiableSet(synchronizedSet);
          }
        }
      }

      return res;
    }
  }

  @Override
  public String toString() {
    return "DefaultPicoContainer" + (getParent() == null ? " (root)" : " (parent="+getParent()+")");
  }
}