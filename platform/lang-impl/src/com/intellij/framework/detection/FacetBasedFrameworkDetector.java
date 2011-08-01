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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.framework.FrameworkType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class FacetBasedFrameworkDetector<F extends Facet, C extends FacetConfiguration> extends FrameworkDetector {
  public FacetBasedFrameworkDetector(String detectorId) {
    super(detectorId);
  }

  public abstract FacetType<F, C> getFacetType();

  @NotNull
  public abstract List<Pair<C,Collection<VirtualFile>>> createConfigurations(@NotNull Collection<VirtualFile> files,
                                                                             @NotNull Collection<C> existentFacetConfigurations);

  public void setupFacet(@NotNull F facet) {
  }

  @Override
  public List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<VirtualFile> newFiles,
                                                             @NotNull FrameworkDetectionContext context) {
    return context.createDetectedFacetDescriptions(this, newFiles);
  }

  @Override
  public FrameworkType getFrameworkType() {
    return new FrameworkType(getFacetType().getStringId(), getFacetType().getPresentableName(), getFacetType().getIcon());
  }
}
