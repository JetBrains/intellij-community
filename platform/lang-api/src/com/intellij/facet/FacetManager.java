// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetManager implements FacetModel {
  public static final Topic<FacetManagerListener> FACETS_TOPIC = Topic.create("facet changes", FacetManagerListener.class, Topic.BroadcastDirection.TO_PARENT);

  public static FacetManager getInstance(@NotNull Module module) {
    return module.getComponent(FacetManager.class);
  }

  /**
   * Creates the interface for modifying set of facets in the module. Call {@link ModifiableFacetModel#commit()} when modification is finished
   * @return the modifiable facet model
   */
  @NotNull
  public abstract ModifiableFacetModel createModifiableModel();

  @NotNull
  public abstract <F extends Facet<?>, C extends FacetConfiguration> F createFacet(@NotNull FacetType<F, C> type, @NotNull String name,
                                                                                   @NotNull C configuration, @Nullable Facet<?> underlying);


  @NotNull
  public abstract <F extends Facet<?>, C extends FacetConfiguration> F createFacet(@NotNull FacetType<F, C> type, @NotNull String name,
                                                                                   @Nullable Facet<?> underlying);

  @NotNull
  public abstract <F extends Facet<?>, C extends FacetConfiguration> F addFacet(@NotNull FacetType<F, C> type, @NotNull String name,
                                                                                   @Nullable Facet<?> underlying);

}
