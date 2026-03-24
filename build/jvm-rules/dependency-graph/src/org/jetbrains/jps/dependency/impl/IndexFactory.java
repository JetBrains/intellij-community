// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.BackDependencyIndex;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.dependency.kotlin.TypealiasesIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.map;

public interface IndexFactory {
  Iterable<BackDependencyIndex> createIndices(@NotNull MapletFactory cFactory);


  /**
   * An index factory creating set of mandatory indices that must be present in all graph instances
   */
  static IndexFactory mandatoryIndices() {
    return containerFactory -> List.of(
      new NodeDependenciesIndex(containerFactory),
      new SubclassesIndex(containerFactory),
      new TypealiasesIndex(containerFactory)
    );
  }

  /**
   *
   * @param extension indices
   * @return a combined index factory that creates all mandatory indices plus those specified
   */
  static IndexFactory create(Function<MapletFactory, BackDependencyIndex>... extIndices) {
    IndexFactory mandatory = mandatoryIndices();
    return containerFactory -> collect(
      map(Arrays.asList(extIndices), extIndex -> extIndex.apply(containerFactory)),
      collect(mandatory.createIndices(containerFactory), new ArrayList<>())
    );
  }

}
