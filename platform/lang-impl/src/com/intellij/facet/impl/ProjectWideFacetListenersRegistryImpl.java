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

package com.intellij.facet.impl;

import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ProjectWideFacetListenersRegistryImpl extends ProjectWideFacetListenersRegistry {
  private final Map<FacetTypeId, EventDispatcher<ProjectWideFacetListener>> myDispatchers = new HashMap<>();
  private final Map<FacetTypeId, WeakHashMap<Facet, Boolean>> myFacetsByType = new HashMap<>();
  private final Map<Module, MessageBusConnection> myModule2Connection = new HashMap<>();
  private final FacetManagerAdapter myFacetListener;
  private final EventDispatcher<ProjectWideFacetListener> myAllFacetsListener = EventDispatcher.create(ProjectWideFacetListener.class);

  public ProjectWideFacetListenersRegistryImpl(MessageBus messageBus) {
    myFacetListener = new MyFacetManagerAdapter();
    messageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        onModuleAdded(module);
      }

      @Override
      public void beforeModuleRemoved(@NotNull final Project project, @NotNull final Module module) {
        Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
        for (Facet facet : allFacets) {
          onFacetRemoved(facet, true);
        }
      }

      @Override
      public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
        onModuleRemoved(module);
      }
    });
  }

  private void onModuleRemoved(final Module module) {
    final MessageBusConnection connection = myModule2Connection.remove(module);
    if (connection != null) {
      connection.disconnect();
    }

    final FacetManager facetManager = FacetManager.getInstance(module);
    final Facet[] facets = facetManager.getAllFacets();
    for (Facet facet : facets) {
      onFacetRemoved(facet, false);
    }
  }

  private void onModuleAdded(final Module module) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    final Facet[] facets = facetManager.getAllFacets();
    for (Facet facet : facets) {
      onFacetAdded(facet);
    }
    final MessageBusConnection connection = module.getMessageBus().connect();
    myModule2Connection.put(module, connection);
    connection.subscribe(FacetManager.FACETS_TOPIC, myFacetListener);
  }

  private void onFacetRemoved(final Facet facet, final boolean before) {
    final FacetTypeId typeId = facet.getTypeId();
    WeakHashMap<Facet, Boolean> facets = myFacetsByType.get(typeId);
    boolean lastFacet;
    if (facets != null) {
      facets.remove(facet);
      lastFacet = facets.isEmpty();
      if (lastFacet) {
        myFacetsByType.remove(typeId);
      }
    }
    else {
      lastFacet = true;
    }
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      if (before) {
        //noinspection unchecked
        dispatcher.getMulticaster().beforeFacetRemoved(facet);
      }
      else {
        //noinspection unchecked
        dispatcher.getMulticaster().facetRemoved(facet);
        if (lastFacet) {
          dispatcher.getMulticaster().allFacetsRemoved();
        }
      }
    }

    if (before) {
      getAllFacetsMulticaster().beforeFacetRemoved(facet);
    }
    else {
      getAllFacetsMulticaster().facetRemoved(facet);
      if (myFacetsByType.isEmpty()) {
        getAllFacetsMulticaster().allFacetsRemoved();
      }
    }
  }

  private ProjectWideFacetListener<Facet> getAllFacetsMulticaster() {
    //noinspection unchecked
    return myAllFacetsListener.getMulticaster();
  }

  private void onFacetAdded(final Facet facet) {
    boolean firstFacet = myFacetsByType.isEmpty();
    final FacetTypeId typeId = facet.getTypeId();
    WeakHashMap<Facet, Boolean> facets = myFacetsByType.get(typeId);
    if (facets == null) {
      facets = new WeakHashMap<>();
      myFacetsByType.put(typeId, facets);
    }
    boolean firstFacetOfType = facets.isEmpty();
    facets.put(facet, true);

    if (firstFacet) {
      getAllFacetsMulticaster().firstFacetAdded();
    }
    getAllFacetsMulticaster().facetAdded(facet);

    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      if (firstFacetOfType) {
        dispatcher.getMulticaster().firstFacetAdded();
      }
      //noinspection unchecked
      dispatcher.getMulticaster().facetAdded(facet);
    }
  }

  private void onFacetChanged(final Facet facet) {
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(facet.getTypeId());
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().facetConfigurationChanged(facet);
    }
    getAllFacetsMulticaster().facetConfigurationChanged(facet);
  }

  @Override
  public <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener) {
    EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(ProjectWideFacetListener.class);
      myDispatchers.put(typeId, dispatcher);
    }
    dispatcher.addListener(listener);
  }

  @Override
  public <F extends Facet> void unregisterListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener) {
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      dispatcher.removeListener(listener);
    }
  }

  @Override
  public <F extends Facet> void registerListener(@NotNull final FacetTypeId<F> typeId, @NotNull final ProjectWideFacetListener<? extends F> listener,
                                                 @NotNull final Disposable parentDisposable) {
    registerListener(typeId, listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterListener(typeId, listener);
      }
    });
  }

  @Override
  public void registerListener(@NotNull final ProjectWideFacetListener<Facet> listener) {
    myAllFacetsListener.addListener(listener);
  }

  @Override
  public void unregisterListener(@NotNull final ProjectWideFacetListener<Facet> listener) {
    myAllFacetsListener.removeListener(listener);
  }

  @Override
  public void registerListener(@NotNull final ProjectWideFacetListener<Facet> listener, @NotNull final Disposable parentDisposable) {
    myAllFacetsListener.addListener(listener, parentDisposable);
  }

  private class MyFacetManagerAdapter extends FacetManagerAdapter {

    @Override
    public void facetAdded(@NotNull Facet facet) {
      onFacetAdded(facet);
    }

    @Override
    public void beforeFacetRemoved(@NotNull final Facet facet) {
      onFacetRemoved(facet, true);
    }

    @Override
    public void facetRemoved(@NotNull Facet facet) {
      onFacetRemoved(facet, false);
    }

    @Override
    public void facetConfigurationChanged(@NotNull final Facet facet) {
      onFacetChanged(facet);
    }

  }
}
