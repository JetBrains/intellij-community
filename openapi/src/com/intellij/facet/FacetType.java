/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FacetType<F extends Facet, C extends FacetConfiguration> {
  private @NotNull FacetTypeId<F> myId;
  private String myPresentableName;
  private @Nullable FacetTypeId myUnderlyingFacetType;

  public FacetType(final FacetTypeId<F> id, final String presentableName, final @Nullable FacetTypeId underlyingFacetType) {
    myId = id;
    myPresentableName = presentableName;
    myUnderlyingFacetType = underlyingFacetType;
  }


  public FacetType(@NotNull final FacetTypeId<F> id, final String presentableName) {
    this(id, presentableName, null);
  }

  @NotNull
  public final FacetTypeId<F> getId() {
    return myId;
  }

  public final String getPresentableName() {
    return myPresentableName;
  }

  @Nullable
  public final FacetTypeId getUnderlyingFacetType() {
    return myUnderlyingFacetType;
  }

  @Nullable
  public FacetDetectingProcessor createFacetDetectingProcessor() {
    return null;
  }

  public abstract C createDefaultConfiguration();

  public abstract F createFacet(@NotNull Module module, @NotNull C configuration, @Nullable Facet underlyingFacet);

  public boolean isOnlyOneFacetAllowed() {
    return false;
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return true;
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }
}
