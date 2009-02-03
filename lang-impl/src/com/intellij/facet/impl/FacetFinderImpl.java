/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class FacetFinderImpl extends FacetFinder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.FacetFinderImpl");
  private final Map<FacetTypeId, AllFacetsOfTypeModificationTracker> myAllFacetTrackers = new HashMap<FacetTypeId, AllFacetsOfTypeModificationTracker>();
  private final Map<FacetTypeId, CachedValue<Map<VirtualFile, List<Facet>>>> myCachedMaps =
    new HashMap<FacetTypeId, CachedValue<Map<VirtualFile, List<Facet>>>>();
  private final Project myProject;
  private final CachedValuesManager myCachedValuesManager;
  private final ModuleManager myModuleManager;

  public FacetFinderImpl(Project project) {
    myProject = project;
    myCachedValuesManager = PsiManager.getInstance(project).getCachedValuesManager();
    myModuleManager = ModuleManager.getInstance(myProject);
  }

  public <F extends Facet> ModificationTracker getAllFacetsOfTypeModificationTracker(FacetTypeId<F> type) {
    AllFacetsOfTypeModificationTracker tracker = myAllFacetTrackers.get(type);
    if (tracker == null) {
      tracker = new AllFacetsOfTypeModificationTracker<F>(myProject, type);
      Disposer.register(myProject, tracker);
      myAllFacetTrackers.put(type, tracker);
    }
    return tracker;
  }

  private <F extends Facet & FacetRootsProvider> Map<VirtualFile, List<Facet>> getRootToFacetsMap(final FacetTypeId<F> type) {
    CachedValue<Map<VirtualFile, List<Facet>>> cachedValue = myCachedMaps.get(type);
    if (cachedValue == null) {
      cachedValue = myCachedValuesManager.createCachedValue(new CachedValueProvider<Map<VirtualFile, List<Facet>>>() {
        public Result<Map<VirtualFile, List<Facet>>> compute() {
          Map<VirtualFile, List<Facet>> map = computeRootToFacetsMap(type);
          return Result.create(map, getAllFacetsOfTypeModificationTracker(type));
        }
      }, false);
      myCachedMaps.put(type, cachedValue);
    }
    final Map<VirtualFile, List<Facet>> value = cachedValue.getValue();
    LOG.assertTrue(value != null);
    return value;
  }

  @NotNull
  private <F extends Facet&FacetRootsProvider> Map<VirtualFile, List<Facet>> computeRootToFacetsMap(final FacetTypeId<F> type) {
    final Module[] modules = myModuleManager.getModules();
    final HashMap<VirtualFile, List<Facet>> map = new HashMap<VirtualFile, List<Facet>>();
    for (Module module : modules) {
      final Collection<F> facets = FacetManager.getInstance(module).getFacetsByType(type);
      for (F facet : facets) {
        for (VirtualFile root : facet.getFacetRoots()) {
          List<Facet> list = map.get(root);
          if (list == null) {
            list = new SmartList<Facet>();
            map.put(root, list);
          }
          list.add(facet);
        }
      }
    }
    return map;
  }

  @Nullable
  public <F extends Facet & FacetRootsProvider> F findFacet(VirtualFile file, FacetTypeId<F> type) {
    final List<F> list = findFacets(file, type);
    return list.size() > 0 ? list.get(0) : null;
  }

  @NotNull
  public <F extends Facet & FacetRootsProvider> List<F> findFacets(VirtualFile file, FacetTypeId<F> type) {
    final Map<VirtualFile, List<Facet>> map = getRootToFacetsMap(type);
    if (!map.isEmpty()) {
      while (file != null) {
        final List<F> list = (List<F>)((List)map.get(file));
        if (list != null) {
          return list;
        }
        file = file.getParent();
      }
    }
    return Collections.<F>emptyList();
  }

  private static class AllFacetsOfTypeModificationTracker<F extends Facet> extends ProjectWideFacetAdapter<F>
                                                                           implements ModificationTracker, Disposable {
    private long myModificationCount;

    public AllFacetsOfTypeModificationTracker(final Project project, final FacetTypeId<F> type) {
      ProjectWideFacetListenersRegistry.getInstance(project).registerListener(type, this, this);
    }

    public void facetAdded(final F facet) {
      myModificationCount++;
    }

    public void facetRemoved(final F facet) {
      myModificationCount++;
    }

    public void facetConfigurationChanged(final F facet) {
      myModificationCount++;
    }

    public void dispose() {
    }

    public long getModificationCount() {
      return myModificationCount;
    }
  }
}
