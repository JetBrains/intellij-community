// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerListener;
import com.intellij.facet.FacetModificationTrackingService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class FacetModificationTrackingServiceImpl extends FacetModificationTrackingService {
  private final ConcurrentMap<Facet<?>, Pair<SimpleModificationTracker, EventDispatcher<ModificationTrackerListener>>> myModificationsTrackers =
    new ConcurrentHashMap<>();

  FacetModificationTrackingServiceImpl(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(FacetManager.FACETS_TOPIC, new FacetModificationTrackingListener(project));
  }

  @Override
  public @NotNull ModificationTracker getFacetModificationTracker(@NotNull Facet<?> facet) {
    return getFacetInfo(facet).first;
  }

  @Override
  public void incFacetModificationTracker() {
    myModificationsTrackers.forEach((facet, pair) -> {
      pair.first.incModificationCount();
      //noinspection unchecked
      pair.second.getMulticaster().modificationCountChanged(facet);
    });
  }

  private Pair<SimpleModificationTracker, EventDispatcher<ModificationTrackerListener>> getFacetInfo(final Facet<?> facet) {
    Pair<SimpleModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = myModificationsTrackers.get(facet);
    if (pair != null) {
      return pair;
    }

    myModificationsTrackers.putIfAbsent(facet, new Pair<>(new SimpleModificationTracker(), EventDispatcher.create(ModificationTrackerListener.class)));
    return myModificationsTrackers.get(facet);
  }

  @Override
  public void incFacetModificationTracker(final @NotNull Facet<?> facet) {
    final Pair<SimpleModificationTracker, EventDispatcher<ModificationTrackerListener>> pair = myModificationsTrackers.get(facet);
    if (pair != null) {
      pair.first.incModificationCount();
      //noinspection unchecked
      pair.second.getMulticaster().modificationCountChanged(facet);
    }
  }

  private final class FacetModificationTrackingListener implements FacetManagerListener {
    private final Project myProject;

    FacetModificationTrackingListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void facetConfigurationChanged(@NotNull Facet facet) {
      if (myProject == facet.getModule().getProject()) {
        incFacetModificationTracker(facet);
      }
    }

    @Override
    public void facetRemoved(@NotNull Facet facet) {
      if (myProject == facet.getModule().getProject()) {
        myModificationsTrackers.remove(facet);
      }
    }
  }
}
