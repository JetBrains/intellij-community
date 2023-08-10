/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.framework.detection;

import com.intellij.facet.*;
import com.intellij.framework.FrameworkType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link FrameworkDetector} for frameworks configured via facets
 */
public abstract class FacetBasedFrameworkDetector<F extends Facet, C extends FacetConfiguration> extends FrameworkDetector {
  private static final Logger LOG = Logger.getInstance(FacetBasedFrameworkDetector.class);

  protected FacetBasedFrameworkDetector(@NonNls String detectorId) {
    super(detectorId);
  }

  protected FacetBasedFrameworkDetector(@NonNls @NotNull String detectorId, int detectorVersion) {
    super(detectorId, detectorVersion);
  }

  @NotNull
  public abstract FacetType<F, C> getFacetType();

  /**
   * Override this method if several facets of this type are allowed in a single module
   * @param files files accepted by detector's filter
   * @param existentFacetConfigurations configuration of facets of this type already added or detected in the module
   * @return configurations with corresponding files
   */
  @NotNull
  public List<Pair<C,Collection<VirtualFile>>> createConfigurations(@NotNull Collection<? extends VirtualFile> files,
                                                                    @NotNull Collection<? extends C> existentFacetConfigurations) {
    final C configuration = createConfiguration(files);
    if (configuration != null) {
      return Collections.singletonList(Pair.create(configuration, (Collection<VirtualFile>)files));
    }
    return Collections.emptyList();
  }

  /**
   * Override this method if only one facet of this type are allowed in a single module
   * @param files files accepted by detector's filter
   * @return configuration for detected facet
   */
  @Nullable
  protected C createConfiguration(Collection<? extends VirtualFile> files) {
    return getFacetType().createDefaultConfiguration();
  }

  public void setupFacet(@NotNull F facet, ModifiableRootModel model) {
  }

  @Override
  public List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<? extends VirtualFile> newFiles,
                                                             @NotNull FrameworkDetectionContext context) {
    return context.createDetectedFacetDescriptions(this, newFiles);
  }

  @Override
  public @NotNull FrameworkType getFrameworkType() {
    FacetType<F, C> type = getFacetType();
    //noinspection ConstantConditions todo[nik] remove later: this is added to find implementations which incorrectly return 'null' from 'getFacetType'
    LOG.assertTrue(type != null, "'getFacetType' returns 'null' in " + getClass());
    return createFrameworkType(type);
  }

  static FrameworkType createFrameworkType(final FacetType<?, ?> facetType) {
    return new FacetBasedFrameworkType(facetType);
  }

  @Override
  public FrameworkType getUnderlyingFrameworkType() {
    final FacetTypeId<?> underlyingTypeId = getFacetType().getUnderlyingFacetType();
    return underlyingTypeId != null ? createFrameworkType(FacetTypeRegistry.getInstance().findFacetType(underlyingTypeId)) : null;
  }

  public boolean isSuitableUnderlyingFacetConfiguration(FacetConfiguration underlying, C configuration, Set<? extends VirtualFile> files) {
    return true;
  }

  private static class FacetBasedFrameworkType extends FrameworkType {
    private final FacetType<?, ?> myFacetType;

    FacetBasedFrameworkType(@NotNull FacetType<?, ?> facetType) {
      super(facetType.getStringId());
      myFacetType = facetType;
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return myFacetType.getPresentableName();
    }

    @NotNull
    @Override
    public Icon getIcon() {
      Icon icon = myFacetType.getIcon();
      return icon != null ? icon : EmptyIcon.ICON_16;
    }
  }
}
