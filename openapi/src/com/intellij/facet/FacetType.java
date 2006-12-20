/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FacetType<F extends Facet, C extends FacetConfiguration> {
  private @NotNull FacetTypeId<F> myId;
  private String myStringId;
  private String myPresentableName;
  private @Nullable FacetTypeId myUnderlyingFacetType;

  public FacetType(final @NotNull FacetTypeId<F> id, final @NotNull @NonNls String stringId, final @NotNull String presentableName, final @Nullable FacetTypeId underlyingFacetType) {
    myId = id;
    myStringId = stringId;
    myPresentableName = presentableName;
    myUnderlyingFacetType = underlyingFacetType;
  }


  public FacetType(final @NotNull FacetTypeId<F> id, final @NotNull @NonNls String stringId, final @NotNull String presentableName) {
    this(id, stringId, presentableName, null);
  }

  @NotNull
  public final FacetTypeId<F> getId() {
    return myId;
  }

  public final String getStringId() {
    return myStringId;
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
