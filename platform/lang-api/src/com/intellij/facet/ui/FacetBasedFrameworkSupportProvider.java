/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.facet.ui;

import com.intellij.facet.*;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderBase;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Provides facet-based framework support.
 *
 * @author nik
 */
public abstract class FacetBasedFrameworkSupportProvider<F extends Facet> extends FrameworkSupportProviderBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.ui.FacetBasedFrameworkSupportProvider");
  @NonNls private static final String FACET_SUPPORT_PREFIX = "facet:";
  private final FacetType<F, ?> myFacetType;

  protected FacetBasedFrameworkSupportProvider(FacetType<F, ?> facetType) {
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
  public static String getProviderId(final FacetType facetType) {
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
    FacetType<?,?> type = FacetTypeRegistry.getInstance().findFacetType(typeId);
    LOG.assertTrue(type != null, typeId);
    return getProviderId(type);
  }

  @Nullable
  public String getUnderlyingFrameworkId() {
    FacetTypeId<?> typeId = myFacetType.getUnderlyingFacetType();
    if (typeId == null) return null;

    FacetType<?,?> type = FacetTypeRegistry.getInstance().findFacetType(typeId);
    return type != null ? getProviderId(type) : null;

  }

  public boolean isEnabledForModuleType(@NotNull final ModuleType moduleType) {
    return myFacetType.isSuitableModuleType(moduleType);
  }

  public boolean isSupportAlreadyAdded(@NotNull final Module module) {
    return !FacetManager.getInstance(module).getFacetsByType(myFacetType.getId()).isEmpty();
  }

  public Icon getIcon() {
    return myFacetType.getIcon();
  }

  protected void addSupport(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel, final FrameworkVersion version, final @Nullable Library library) {
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
  protected void onFacetCreated(final F facet, final ModifiableRootModel rootModel, final FrameworkVersion version) {
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
   *
   * @param module         Module.
   * @param addedLibraries Framework libraries.
   */
  public void processAddedLibraries(final Module module, final List<Library> addedLibraries) {
  }
}
