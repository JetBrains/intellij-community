/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetManager implements FacetModel {
  public static final Topic<FacetManagerListener> FACETS_TOPIC = Topic.create("facet changes", FacetManagerListener.class);

  public static FacetManager getInstance(Module module) {
    return module.getComponent(FacetManager.class);
  }

  @NotNull
  public abstract ModifiableFacetModel createModifiableModel();


}
