// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public abstract class FacetManager implements FacetModel {

  @Topic.ProjectLevel
  public static final Topic<FacetManagerListener> FACETS_TOPIC = new Topic<>(FacetManagerListener.class, Topic.BroadcastDirection.TO_PARENT);

  public static FacetManager getInstance(@NotNull Module module) {
    return module.getComponent(FacetManager.class);
  }

  /**
   * Creates the interface for modifying set of facets in the module. Call {@link ModifiableFacetModel#commit()} when modification is finished
   * @return the modifiable facet model
   */
  public abstract @NotNull ModifiableFacetModel createModifiableModel();

  public abstract @NotNull <F extends Facet<?>, C extends FacetConfiguration> F createFacet(@NotNull FacetType<F, C> type, @NotNull String name,
                                                                                            @NotNull C configuration, @Nullable Facet<?> underlying);


  public abstract @NotNull <F extends Facet<?>, C extends FacetConfiguration> F createFacet(@NotNull FacetType<F, C> type, @NotNull String name,
                                                                                            @Nullable Facet<?> underlying);

  public abstract @NotNull <F extends Facet<?>, C extends FacetConfiguration> F addFacet(@NotNull FacetType<F, C> type, @NotNull String name,
                                                                                         @Nullable Facet<?> underlying);

  /**
   * This method must be called when configuration of {@code facet} is changed via its API.
   */
  public abstract void facetConfigurationChanged(@NotNull Facet<?> facet);
}
