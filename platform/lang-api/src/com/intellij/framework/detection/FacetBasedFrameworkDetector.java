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
package com.intellij.framework.detection;

import com.intellij.facet.*;
import com.intellij.framework.FrameworkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link FrameworkDetector} for frameworks configured via facets
 *
 * @author nik
 */
public abstract class FacetBasedFrameworkDetector<F extends Facet, C extends FacetConfiguration> extends FrameworkDetector {
  public FacetBasedFrameworkDetector(String detectorId) {
    super(detectorId);
  }

  public abstract FacetType<F, C> getFacetType();

  /**
   * Override this method if several facets of this type are allowed in a single module
   * @param files files accepted by detector's filter
   * @param existentFacetConfigurations configuration of facets of this type already added or detected in the module
   * @return configurations with corresponding files
   */
  @NotNull
  public List<Pair<C,Collection<VirtualFile>>> createConfigurations(@NotNull Collection<VirtualFile> files,
                                                                    @NotNull Collection<C> existentFacetConfigurations) {
    final C configuration = createConfiguration(files);
    if (configuration != null) {
      return Collections.singletonList(Pair.create(configuration, files));
    }
    return Collections.emptyList();
  }

  /**
   * Override this method if only one facet of this type are allowed in a single module
   * @param files files accepted by detector's filter
   * @return configuration for detected facet
   */
  @Nullable
  protected C createConfiguration(Collection<VirtualFile> files) {
    return getFacetType().createDefaultConfiguration();
  }

  public void setupFacet(@NotNull F facet, ModifiableRootModel model) {
  }

  @Override
  public List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<VirtualFile> newFiles,
                                                             @NotNull FrameworkDetectionContext context) {
    return context.createDetectedFacetDescriptions(this, newFiles);
  }

  @Override
  public FrameworkType getFrameworkType() {
    return createFrameworkType(getFacetType());
  }

  static FrameworkType createFrameworkType(final FacetType<?, ?> facetType) {
    return new FacetBasedFrameworkType(facetType);
  }

  @Override
  public FrameworkType getUnderlyingFrameworkType() {
    final FacetTypeId<?> underlyingTypeId = getFacetType().getUnderlyingFacetType();
    return underlyingTypeId != null ? createFrameworkType(FacetTypeRegistry.getInstance().findFacetType(underlyingTypeId)) : null;
  }

  public boolean isSuitableUnderlyingFacetConfiguration(FacetConfiguration underlying, C configuration, Set<VirtualFile> files) {
    return true;
  }

  private static class FacetBasedFrameworkType extends FrameworkType {
    private final FacetType<?, ?> myFacetType;
    private final Icon myIcon;

    public FacetBasedFrameworkType(FacetType<?, ?> facetType) {
      super(facetType.getStringId());
      myFacetType = facetType;
      final Icon icon = myFacetType.getIcon();
      myIcon = icon != null ? icon : EmptyIcon.ICON_16;
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return myFacetType.getPresentableName();
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return myIcon;
    }
  }
}
