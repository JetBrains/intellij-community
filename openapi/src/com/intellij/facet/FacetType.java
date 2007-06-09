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
 *
 * @see com.intellij.facet.FacetTypeRegistry#registerFacetType(FacetType) 
 */
public abstract class FacetType<F extends Facet, C extends FacetConfiguration> {
  private final @NotNull FacetTypeId<F> myId;
  private final @NotNull String myStringId;
  private final @NotNull String myPresentableName;
  private final @Nullable FacetTypeId myUnderlyingFacetType;

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

  @NotNull
  public final String getStringId() {
    return myStringId;
  }

  @NotNull
  public final String getPresentableName() {
    return myPresentableName;
  }

  @NotNull @NonNls
  public String getDefaultFacetName() {
    return myPresentableName;
  }

  @Nullable
  public final FacetTypeId getUnderlyingFacetType() {
    return myUnderlyingFacetType;
  }


  public abstract C createDefaultConfiguration();

  /**
   * Create a facet instance
   * @param module parent module for facet. Must be passed to {@link Facet} constructor
   * @param name name of facet. Must be passed to {@link Facet} constructor
   * @param configuration facet configuration. Must be passed to {@link Facet} constructor
   * @param underlyingFacet underlying facet. Must be passed to {@link Facet} constructor 
   * @return a created facet
   */
  public abstract F createFacet(@NotNull Module module, final String name, @NotNull C configuration, @Nullable Facet underlyingFacet);

  public boolean isOnlyOneFacetAllowed() {
    return true;
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType == ModuleType.JAVA;
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }
}
