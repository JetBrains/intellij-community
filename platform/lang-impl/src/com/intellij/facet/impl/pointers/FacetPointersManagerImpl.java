// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FacetPointersManagerImpl extends FacetPointersManager {
  private final Map<String, FacetPointerImpl> myPointers = new HashMap<>();
  private final Map<Class<? extends Facet>, EventDispatcher<FacetPointerListener>> myDispatchers = new HashMap<>();
  private final @NotNull Project myProject;

  public FacetPointersManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public <F extends Facet> FacetPointer<F> create(final F facet) {
    String id = constructId(facet);
    //noinspection unchecked
    FacetPointerImpl<F> pointer = myPointers.get(id);
    if (pointer == null) {
      if (!FacetUtil.isRegistered(facet)) {
        return create(id);
      }
      pointer = new FacetPointerImpl<>(this, facet);
      myPointers.put(id, pointer);
    }
    return pointer;
  }

  @Override
  public <F extends Facet> FacetPointer<F> create(final String id) {
    //noinspection unchecked
    FacetPointerImpl<F> pointer = myPointers.get(id);
    if (pointer == null) {
      pointer = new FacetPointerImpl<>(this, id);
      myPointers.put(id, pointer);
    }
    return pointer;
  }

  <F extends Facet> void dispose(final FacetPointer<F> pointer) {
    myPointers.remove(pointer.getId());
  }

  void refreshPointers() {
    //todo refresh only pointers related to renamed module/facet?
    List<Pair<FacetPointerImpl, String>> changed = new ArrayList<>();

    for (FacetPointerImpl pointer : myPointers.values()) {
      final String oldId = pointer.getId();
      pointer.refresh();
      if (!oldId.equals(pointer.getId())) {
        changed.add(Pair.create(pointer, oldId));
      }
    }

    for (Pair<FacetPointerImpl, String> pair : changed) {
      FacetPointerImpl pointer = pair.getFirst();
      final Facet facet = pointer.getFacet();
      Class facetClass = facet != null ? facet.getClass() : Facet.class;
      while (facetClass != Object.class) {
        final EventDispatcher<FacetPointerListener> dispatcher = myDispatchers.get(facetClass);
        if (dispatcher != null) {
          //noinspection unchecked
          dispatcher.getMulticaster().pointerIdChanged(pointer, pair.getSecond());
        }
        facetClass = facetClass.getSuperclass();
      }
    }
  }

  public boolean isRegistered(FacetPointer<?> pointer) {
    return myPointers.containsKey(pointer.getId());
  }

  FacetPointerImpl get(String id) {
    return myPointers.get(id);
  }

  @Override
  public void addListener(final FacetPointerListener<Facet> listener) {
    addListener(Facet.class, listener);
  }

  @Override
  public void removeListener(final FacetPointerListener<Facet> listener) {
    removeListener(Facet.class, listener);
  }

  @Override
  public void addListener(final FacetPointerListener<Facet> listener, final Disposable parentDisposable) {
    addListener(Facet.class, listener, parentDisposable);
  }

  @Override
  public <F extends Facet> void addListener(final Class<F> facetClass, final FacetPointerListener<F> listener, final Disposable parentDisposable) {
    addListener(facetClass, listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeListener(facetClass, listener);
      }
    });
  }

  @Override
  public <F extends Facet> void addListener(final Class<F> facetClass, final FacetPointerListener<F> listener) {
    EventDispatcher<FacetPointerListener> dispatcher = myDispatchers.get(facetClass);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(FacetPointerListener.class);
      myDispatchers.put(facetClass, dispatcher);
    }
    dispatcher.addListener(listener);
  }

  @Override
  public <F extends Facet> void removeListener(final Class<F> facetClass, final FacetPointerListener<F> listener) {
    EventDispatcher<FacetPointerListener> dispatcher = myDispatchers.get(facetClass);
    if (dispatcher != null) {
      dispatcher.removeListener(listener);
    }
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
