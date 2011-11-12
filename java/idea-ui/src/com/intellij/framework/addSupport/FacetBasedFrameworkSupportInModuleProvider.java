/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.addSupport;

import com.intellij.facet.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FacetBasedFrameworkSupportInModuleProvider<F extends Facet> extends FrameworkSupportInModuleProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.framework.addSupport.FacetBasedFrameworkSupportInModuleProvider");
  private final FacetType<F, ?> myFacetType;

  protected FacetBasedFrameworkSupportInModuleProvider(FacetType<F, ?> facetType) {
    myFacetType = facetType;
  }

  public boolean isEnabledForModuleType(@NotNull final ModuleType moduleType) {
    return myFacetType.isSuitableModuleType(moduleType);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return !FacetManager.getInstance(module).getFacetsByType(myFacetType.getId()).isEmpty();
  }


  protected class FacetBasedFrameworkSupportInModuleConfigurable extends FrameworkSupportInModuleConfigurable {
    @Override
    public JComponent createComponent() {
      return null;
    }

    @Override
    public void addSupport(@NotNull Module module,
                           @NotNull ModifiableRootModel rootModel,
                           @NotNull ModifiableModelsProvider modifiableModelsProvider) {
      FacetManager facetManager = FacetManager.getInstance(module);
      ModifiableFacetModel model = facetManager.createModifiableModel();
      Facet underlyingFacet = null;
      FacetTypeId<?> underlyingFacetType = myFacetType.getUnderlyingFacetType();
      if (underlyingFacetType != null) {
        underlyingFacet = model.getFacetByType(underlyingFacetType);
        LOG.assertTrue(underlyingFacet != null, underlyingFacetType);
      }
      F facet = facetManager.createFacet(myFacetType, myFacetType.getDefaultFacetName(), underlyingFacet);
      setupConfiguration(facet, rootModel);
      model.addFacet(facet);
      model.commit();
      onFacetCreated(facet, rootModel);
    }

    protected void setupConfiguration(final F facet, final ModifiableRootModel rootModel) {
    }

    protected void onFacetCreated(final F facet, final ModifiableRootModel rootModel) {
    }

  }
}
