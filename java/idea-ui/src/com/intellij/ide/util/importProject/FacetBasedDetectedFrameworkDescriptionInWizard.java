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
package com.intellij.ide.util.importProject;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetTypeId;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.impl.FacetBasedDetectedFrameworkDescription;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author nik
 */
public class FacetBasedDetectedFrameworkDescriptionInWizard<F extends Facet, C extends FacetConfiguration> extends FacetBasedDetectedFrameworkDescription<F, C> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.importProject.FacetBasedDetectedFrameworkDescriptionInWizard");
  private final ModuleDescriptor myModuleDescriptor;

  public FacetBasedDetectedFrameworkDescriptionInWizard(@NotNull ModuleDescriptor moduleDescriptor,
                                                        FacetBasedFrameworkDetector<F, C> detector,
                                                        @NotNull C configuration,
                                                        Set<VirtualFile> files) {
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

  @NotNull
  protected Collection<? extends Facet> getExistentFacets(FacetTypeId<?> underlyingFacetType) {
    return Collections.emptyList();
  }
}
