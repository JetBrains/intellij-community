// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.ui;

import com.intellij.facet.*;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderBase;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Provides facet-based framework support.
 */
public abstract class FacetBasedFrameworkSupportProvider<F extends Facet> extends FrameworkSupportProviderBase {
  private static final Logger LOG = Logger.getInstance(FacetBasedFrameworkSupportProvider.class);
  private static final @NonNls String FACET_SUPPORT_PREFIX = "facet:";
  private final FacetType<F, ?> myFacetType;

  protected FacetBasedFrameworkSupportProvider(@NotNull FacetType<F, ?> facetType) {
    super(getProviderId(facetType), facetType.getPresentableName());
    myFacetType = facetType;
  }

  /**
   * Returns internal ID.
   *
   * @param facetType Facet type.
   * @return ID.
   * @see #getPrecedingFrameworkProviderIds()
   */
  public static String getProviderId(@NotNull FacetType<?, ?> facetType) {
    return FACET_SUPPORT_PREFIX + facetType.getStringId();
  }

  /**
   * Returns internal ID.
   *
   * @param typeId Facet type ID.
   * @return ID.
   * @see #getPrecedingFrameworkProviderIds()
   */
  public static String getProviderId(final FacetTypeId<?> typeId) {
    return getProviderId(FacetTypeRegistry.getInstance().findFacetType(typeId));
  }

  @Override
  public @Nullable String getUnderlyingFrameworkId() {
    FacetTypeId<?> typeId = myFacetType.getUnderlyingFacetType();
    if (typeId == null) return null;

    return getProviderId(FacetTypeRegistry.getInstance().findFacetType(typeId));

  }

  @Override
  public boolean isEnabledForModuleType(final @NotNull ModuleType moduleType) {
    return myFacetType.isSuitableModuleType(moduleType);
  }

  @Override
  public boolean isSupportAlreadyAdded(final @NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return !facetsProvider.getFacetsByType(module, myFacetType.getId()).isEmpty();
  }

  @Override
  public Icon getIcon() {
    return myFacetType.getIcon();
  }

  @Override
  protected void addSupport(final @NotNull Module module, final @NotNull ModifiableRootModel rootModel, final FrameworkVersion version, final @Nullable Library library) {
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    Facet underlyingFacet = null;
    FacetTypeId<?> underlyingFacetType = myFacetType.getUnderlyingFacetType();
    if (underlyingFacetType != null) {
      underlyingFacet = model.getFacetByType(underlyingFacetType);
      LOG.assertTrue(underlyingFacet != null, underlyingFacetType);
    }
    F facet = facetManager.createFacet(myFacetType, myFacetType.getDefaultFacetName(), underlyingFacet);
    setupConfiguration(facet, rootModel, version);
    if (library != null) {
      onLibraryAdded(facet, library);
    }
    model.addFacet(facet);
    model.commit();
    onFacetCreated(facet, rootModel, version);
  }

  /**
   * Called last after facet and library have been setup.
   *
   * @param facet     Created facet.
   * @param rootModel Model.
   * @param version   Framework version.
   */
  protected void onFacetCreated(final @NotNull F facet, final @NotNull ModifiableRootModel rootModel, final FrameworkVersion version) {
  }

  protected void onLibraryAdded(final F facet, final @NotNull Library library) {
  }

  /**
   * Tune facet before it is added.
   *
   * @param facet     Facet to be created.
   * @param rootModel Model.
   * @param version   Framework version.
   */
  protected abstract void setupConfiguration(final F facet, final ModifiableRootModel rootModel, final FrameworkVersion version);

  /**
   * Override to e.g. add libraries to artifacts.
   *  @param module         Module.
   * @param addedLibraries Framework libraries.
   */
  public void processAddedLibraries(final Module module, final List<? extends Library> addedLibraries) {
  }
}
