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

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.FacetModificationTrackingService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.ModificationTrackerListener;
import com.intellij.openapi.util.Pair;
import com.intellij.util.EventDispatcher;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author nik
 */
public class FacetModificationTrackingServiceImpl extends FacetModificationTrackingService {
  private final Map<Facet, Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>>> myModificationsTrackers =
    new THashMap<Facet, Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>>>();

  public FacetModificationTrackingServiceImpl(final Module module) {
    module.getMessageBus().connect().subscribe(FacetManager.FACETS_TOPIC, new FacetModificationTrackingListener());
  }

  @Override
  @NotNull
  public FacetModificationTracker getFacetModificationTracker(@NotNull final Facet facet) {
    return getFacetInfo(facet).first;
  }

  private Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>> getFacetInfo(final Facet facet) {
    Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = myModificationsTrackers.get(facet);
    if (pair == null) {
      pair = Pair.create(new FacetModificationTracker(), EventDispatcher.create(ModificationTrackerListener.class));
      myModificationsTrackers.put(facet, pair);
    }
    return pair;
  }

  @Override
  public void incFacetModificationTracker(@NotNull final Facet facet) {
    final Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = getFacetInfo(facet);
    pair.first.myModificationCount ++;
    pair.second.getMulticaster().modificationCountChanged(facet);
  }

  @Override
  public <T extends Facet> void addModificationTrackerListener(final T facet, final ModificationTrackerListener<? super T> listener, final Disposable parent) {
    getFacetInfo(facet).second.addListener(listener, parent);
  }

  @Override
  public void removeModificationTrackerListener(final Facet facet, final ModificationTrackerListener<?> listener) {
    getFacetInfo(facet).second.removeListener(listener);
  }

  private static class FacetModificationTracker implements ModificationTracker {
    private long myModificationCount;

    @Override
    public long getModificationCount() {
      return myModificationCount;
    }
  }

  private class FacetModificationTrackingListener extends FacetManagerAdapter {
    @Override
    public void facetConfigurationChanged(@NotNull final Facet facet) {
      final Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = myModificationsTrackers.get(facet);
      if (pair != null) {
        pair.first.myModificationCount++;
        pair.second.getMulticaster().modificationCountChanged(facet);
      }
    }

    @Override
    public void facetRemoved(@NotNull final Facet facet) {
      myModificationsTrackers.remove(facet);
    }
  }
}
