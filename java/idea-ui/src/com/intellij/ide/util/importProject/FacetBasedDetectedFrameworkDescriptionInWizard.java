// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetTypeId;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.impl.FacetBasedDetectedFrameworkDescription;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class FacetBasedDetectedFrameworkDescriptionInWizard<F extends Facet, C extends FacetConfiguration> extends FacetBasedDetectedFrameworkDescription<F, C> {
  private static final Logger LOG = Logger.getInstance(FacetBasedDetectedFrameworkDescriptionInWizard.class);
  private final ModuleDescriptor myModuleDescriptor;

  public FacetBasedDetectedFrameworkDescriptionInWizard(@NotNull ModuleDescriptor moduleDescriptor,
                                                        FacetBasedFrameworkDetector<F, C> detector,
                                                        @NotNull C configuration,
                                                        Set<? extends VirtualFile> files) {
    super(detector, configuration, files);
    myModuleDescriptor = moduleDescriptor;
  }

  @Override
  protected String getModuleName() {
    return myModuleDescriptor.getName();
  }

  @Override
  public void setupFramework(@NotNull ModifiableModelsProvider modifiableModelsProvider, @NotNull ModulesProvider modulesProvider) {
    Module module = modulesProvider.getModule(getModuleName());
    LOG.assertTrue(module != null, getModuleName());
    doSetup(modifiableModelsProvider, module);
  }

  @Override
  @NotNull
  protected Collection<? extends Facet> getExistentFacets(FacetTypeId<?> underlyingFacetType) {
    return Collections.emptyList();
  }
}
