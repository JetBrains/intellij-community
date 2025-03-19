// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public final class FacetBasedDetectedFrameworkDescriptionImpl<F extends Facet, C extends FacetConfiguration> extends FacetBasedDetectedFrameworkDescription<F, C> {
  private final Module myModule;

  public FacetBasedDetectedFrameworkDescriptionImpl(@NotNull Module module,
                                                    FacetBasedFrameworkDetector<F, C> detector,
                                                    @NotNull C configuration,
                                                    Set<? extends VirtualFile> files) {
    super(detector, configuration, files);
    myModule = module;
  }

  @Override
  protected String getModuleName() {
    return myModule.getName();
  }

  @Override
  public void setupFramework(@NotNull ModifiableModelsProvider modifiableModelsProvider, @NotNull ModulesProvider modulesProvider) {
    doSetup(modifiableModelsProvider, myModule);
  }

  @Override
  protected @NotNull Collection<? extends Facet> getExistentFacets(FacetTypeId<?> underlyingFacetType) {
    return FacetManager.getInstance(myModule).getFacetsByType(underlyingFacetType);
  }
}
