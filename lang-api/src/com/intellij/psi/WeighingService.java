/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collection;

/**
 * @author peter
 */
public class WeighingService {
  private final NotNullLazyValue<Map<WeigherKey, Weigher[]>> myMap = new NotNullLazyValue<Map<WeigherKey, Weigher[]>>() {
    @NotNull
    protected Map<WeigherKey, Weigher[]> compute() {
      final FactoryMap<WeigherKey, SortedMap<Double,Weigher>> map = new FactoryMap<WeigherKey, SortedMap<Double, Weigher>>() {
        protected SortedMap<Double, Weigher> create(final WeigherKey key) {
          return new TreeMap<Double, Weigher>();
        }
      };
      final WeigherRegistrar registrar = new WeigherRegistrar() {
        public <T, Loc> void registerWeigher(final WeigherKey<T, Loc> key, final double priority, final Weigher<T, Loc> weigher) {
          map.get(key).put(priority, weigher);
        }
      };
      for (final WeighingContributor contributor : Extensions.getExtensions(WeighingContributor.EP_NAME)) {
        contributor.registerWeighers(registrar);
      }

      final THashMap<WeigherKey, Weigher[]> result = new THashMap<WeigherKey, Weigher[]>();
      for (final WeigherKey key : map.keySet()) {
        final Collection<Weigher> weighers = map.get(key).values();
        result.put(key, weighers.toArray(new Weigher[weighers.size()]));
      }
      return result;
    }
  };

  public <T, Loc> Weigher<T, Loc>[] getWeighers(WeigherKey<T, Loc> key) {
    return myMap.getValue().get(key);
  }

  public static WeighingService getInstance() {
    return ServiceManager.getService(WeighingService.class);
  }

}
