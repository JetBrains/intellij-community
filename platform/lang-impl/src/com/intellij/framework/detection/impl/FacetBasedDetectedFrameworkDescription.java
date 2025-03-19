// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.facet.*;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public abstract class FacetBasedDetectedFrameworkDescription<F extends Facet, C extends FacetConfiguration> extends DetectedFrameworkDescription {
  private static final Logger LOG = Logger.getInstance(FacetBasedDetectedFrameworkDescription.class);
  private final FacetBasedFrameworkDetector<F, C> myDetector;
  private final C myConfiguration;
  private final Set<? extends VirtualFile> myRelatedFiles;
  private final FacetType<F,C> myFacetType;

  public FacetBasedDetectedFrameworkDescription(FacetBasedFrameworkDetector<F, C> detector,
                                                @NotNull C configuration,
                                                Set<? extends VirtualFile> files) {
    myDetector = detector;
    myConfiguration = configuration;
    myRelatedFiles = files;
    myFacetType = detector.getFacetType();
  }

  @Override
  public @NotNull Collection<? extends VirtualFile> getRelatedFiles() {
    return myRelatedFiles;
  }

  public C getConfiguration() {
    return myConfiguration;
  }

  @Override
  public @NotNull String getSetupText() {
    return ProjectBundle.message("label.facet.will.be.added.to.module", myFacetType.getPresentableName(), getModuleName());
  }

  @Override
  public @NotNull FrameworkDetector getDetector() {
    return myDetector;
  }

  protected abstract String getModuleName();

  @Override
  public boolean canSetupFramework(@NotNull Collection<? extends DetectedFrameworkDescription> allDetectedFrameworks) {
    final FacetTypeId<?> underlyingId = myFacetType.getUnderlyingFacetType();
    if (underlyingId == null) {
      return true;
    }

    final Collection<? extends Facet> facets = getExistentFacets(underlyingId);
    for (Facet facet : facets) {
      if (myDetector.isSuitableUnderlyingFacetConfiguration(facet.getConfiguration(), myConfiguration, myRelatedFiles)) {
        return true;
      }
    }
    for (DetectedFrameworkDescription framework : allDetectedFrameworks) {
      if (framework instanceof FacetBasedDetectedFrameworkDescription<?, ?> description) {
        if (underlyingId.equals(description.myFacetType.getId()) &&
            myDetector.isSuitableUnderlyingFacetConfiguration(description.getConfiguration(), myConfiguration, myRelatedFiles)) {
          return true;
        }
      }
    }
    return false;
  }

  protected abstract @NotNull Collection<? extends Facet> getExistentFacets(FacetTypeId<?> underlyingFacetType);

  protected void doSetup(ModifiableModelsProvider modifiableModelsProvider, final Module module) {
    final ModifiableFacetModel model = modifiableModelsProvider.getFacetModifiableModel(module);
    final String name = UniqueNameGenerator.generateUniqueName(myFacetType.getDefaultFacetName(),
                                                               s -> model.findFacet(myFacetType.getId(), s) == null);
    final F facet = FacetManager.getInstance(module).createFacet(myFacetType, name, myConfiguration,
                                                                 findUnderlyingFacet(module));
    model.addFacet(facet);
    modifiableModelsProvider.commitFacetModifiableModel(module, model);
    final ModifiableRootModel rootModel = modifiableModelsProvider.getModuleModifiableModel(module);
    myDetector.setupFacet(facet, rootModel);
    modifiableModelsProvider.commitModuleModifiableModel(rootModel);
  }

  private @Nullable Facet findUnderlyingFacet(Module module) {
    final FacetTypeId<?> underlyingTypeId = myFacetType.getUnderlyingFacetType();
    if (underlyingTypeId == null) return null;

    final Collection<? extends Facet> parentFacets = FacetManager.getInstance(module).getFacetsByType(underlyingTypeId);
    for (Facet facet : parentFacets) {
      if (myDetector.isSuitableUnderlyingFacetConfiguration(facet.getConfiguration(), myConfiguration, myRelatedFiles)) {
        return facet;
      }
    }
    LOG.error("Cannot find suitable underlying facet in " + parentFacets);
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FacetBasedDetectedFrameworkDescription other)) {
      return false;
    }
    return getModuleName().equals(other.getModuleName()) && myFacetType.equals(other.myFacetType) && myRelatedFiles.equals(other.myRelatedFiles);
  }

  @Override
  public int hashCode() {
    return getModuleName().hashCode() + 31*myFacetType.hashCode() + 239*myRelatedFiles.hashCode();
  }
}
