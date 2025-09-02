// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.Map;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class JpsElementContainerImpl extends JpsElementContainerEx implements JpsElementContainer {
  private final Object myDataLock = new Object();
  private final Map<JpsElementChildRole<?>, JpsElement> myElements = new Object2ObjectOpenHashMap<>(1);
  private final @NotNull JpsCompositeElementBase<?> myParent;

  public JpsElementContainerImpl(@NotNull JpsCompositeElementBase<?> parent) {
    myParent = parent;
  }

  public JpsElementContainerImpl(@NotNull JpsElementContainerEx original, @NotNull JpsCompositeElementBase<?> parent) {
    myParent = parent;
    synchronized (original.getDataLock()) {
      for (Map.Entry<JpsElementChildRole<?>, JpsElement> entry : original.getElementsMap().entrySet()) {
        final JpsElementChildRole role = entry.getKey();
        final JpsElement copy = entry.getValue().getBulkModificationSupport().createCopy();
        JpsElementBase.setParent(copy, myParent);
        myElements.put(role, copy);
      }
    }
  }

  @Override
  public <T extends JpsElement> T getChild(@NotNull JpsElementChildRole<T> role) {
    synchronized (myDataLock) {
      //noinspection unchecked
      return (T)myElements.get(role);
    }
  }

  @Override
  public @NotNull <T extends JpsElement, P, K extends JpsElementChildRole<T> & JpsElementParameterizedCreator<T, P>> T setChild(@NotNull K role, @NotNull P param) {
    final T child = role.create(param);
    return setChild(role, child);
  }

  @Override
  public @NotNull <T extends JpsElement, K extends JpsElementChildRole<T> & JpsElementCreator<T>> T setChild(@NotNull K role) {
    final T child = role.create();
    return setChild(role, child);
  }

  @Override
  public @NotNull <T extends JpsElement, K extends JpsElementChildRole<T> & JpsElementCreator<T>> T getOrSetChild(@NotNull K role) {
    synchronized (myDataLock) {
      final T cached = (T)myElements.get(role);
      if (cached != null) {
        return cached;
      }
      return putChild(role, role.create());
    }
  }

  @Override
  public <T extends JpsElement, P, K extends JpsElementChildRole<T> & JpsElementParameterizedCreator<T, P>> T getOrSetChild(@NotNull K role, @NotNull Supplier<P> param) {
    synchronized (myDataLock) {
      final T cached = (T)myElements.get(role);
      if (cached != null) {
        return cached;
      }
      return putChild(role, role.create(param.get()));
    }
  }

  @Override
  public <T extends JpsElement> T setChild(JpsElementChildRole<T> role, T child) {
    synchronized (myDataLock) {
      return putChild(role, child);
    }
  }

  private @NotNull <T extends JpsElement> T putChild(JpsElementChildRole<T> role, T child) {
    JpsElementBase.setParent(child, myParent);
    myElements.put(role, child);
    return child;
  }

  @Override
  public <T extends JpsElement> void removeChild(@NotNull JpsElementChildRole<T> role) {
    //noinspection unchecked
    final T removed;
    synchronized (myDataLock) {
      removed = (T)myElements.remove(role);
    }
    if (removed == null) return;
    JpsElementBase.setParent(removed, null);
  }

  @Override
  public Object getDataLock() {
    return myDataLock;
  }

  @Override
  public Map<JpsElementChildRole<?>, JpsElement> getElementsMap() {
    return myElements;
  }
}
