/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
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

  public void incFacetModificationTracker(@NotNull final Facet facet) {
    final Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = getFacetInfo(facet);
    pair.first.myModificationCount ++;
    pair.second.getMulticaster().modificationCountChanged(facet);
  }

  public <T extends Facet> void addModificationTrackerListener(final T facet, final ModificationTrackerListener<? super T> listener, final Disposable parent) {
    getFacetInfo(facet).second.addListener(listener, parent);
  }

  public void removeModificationTrackerListener(final Facet facet, final ModificationTrackerListener<?> listener) {
    getFacetInfo(facet).second.removeListener(listener);
  }

  private static class FacetModificationTracker implements ModificationTracker {
    private long myModificationCount;

    public long getModificationCount() {
      return myModificationCount;
    }
  }

  private class FacetModificationTrackingListener extends FacetManagerAdapter {
    public void facetConfigurationChanged(@NotNull final Facet facet) {
      final Pair<FacetModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = myModificationsTrackers.get(facet);
      if (pair != null) {
        pair.first.myModificationCount++;
        pair.second.getMulticaster().modificationCountChanged(facet);
      }
    }

    public void facetRemoved(@NotNull final Facet facet) {
      myModificationsTrackers.remove(facet);
    }
  }
}
