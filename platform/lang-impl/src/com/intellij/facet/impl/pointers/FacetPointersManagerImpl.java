/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.facet.impl.pointers;

import com.intellij.ProjectTopics;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class FacetPointersManagerImpl extends FacetPointersManager implements ProjectComponent {
  private final Map<String, FacetPointerImpl> myPointers = new HashMap<>();
  private final Map<Class<? extends Facet>, EventDispatcher<FacetPointerListener>> myDispatchers =
    new HashMap<>();

  public FacetPointersManagerImpl(final Project project) {
    super(project);
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

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "FacetPointersManager";
  }

  @Override
  public void initComponent() {
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        refreshPointers(module);
      }

      @Override
      public void modulesRenamed(@NotNull Project project, @NotNull List<Module> modules, @NotNull Function<Module, String> oldNameProvider) {
        for (Module module : modules) {
          refreshPointers(module);
        }
      }
    });
    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void facetAdded(@NotNull Facet facet) {
        refreshPointers(facet.getModule());
      }

      @Override
      public void beforeFacetRenamed(@NotNull Facet facet) {
        final FacetPointerImpl pointer = myPointers.get(constructId(facet));
        if (pointer != null) {
          pointer.refresh();
        }
      }

      @Override
      public void facetRenamed(@NotNull final Facet facet, @NotNull final String oldName) {
        refreshPointers(facet.getModule());
      }
    });
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      refreshPointers(module);
    }
  }

  private void refreshPointers(@NotNull final Module module) {
    //todo[nik] refresh only pointers related to renamed module/facet?
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

  public Project getProject() {
    return myProject;
  }
}
