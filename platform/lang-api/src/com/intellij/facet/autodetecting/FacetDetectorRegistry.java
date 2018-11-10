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

package com.intellij.facet.autodetecting;

import com.intellij.facet.FacetConfiguration;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.VirtualFilePattern;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link com.intellij.framework.detection.FrameworkDetector} instead
 *
 * @author nik
 */
@Deprecated
public interface FacetDetectorRegistry<C extends FacetConfiguration> {
  /**
   * Customize text of popup which will be shown when facet is detected
   * @param presentation
   */
  void customizeDetectedFacetPresentation(@NotNull DetectedFacetPresentation presentation);

  /**
   * Register detector which will be used to detect facet on the fly
   * @param fileType type of facet descriptor files
   * @param virtualFileFilter preliminary filter for facet descriptor file
   * @param psiFileFilter filter for facet descriptors
   * @param detector detector
   *
   * @deprecated do not use facet detectors based on PsiFile, because it may slow down facet detection process.
   * Use {@link #registerUniversalDetector} instead
   */
  @Deprecated
  void registerOnTheFlyDetector(@NotNull FileType fileType, @NotNull VirtualFileFilter virtualFileFilter, @NotNull Condition<PsiFile> psiFileFilter,
                                @NotNull FacetDetector<PsiFile, C> detector);

  /**
   * @deprecated do not use facet detectors based on PsiFile, because it may slow down facet detection process.
   * Use {@link #registerUniversalDetector} instead
   */
  @Deprecated
  void registerOnTheFlyDetector(@NotNull FileType fileType, @NotNull VirtualFilePattern virtualFilePattern,
                                @NotNull ElementPattern<? extends PsiFile> psiFilePattern, @NotNull FacetDetector<PsiFile, C> facetDetector);

  /**
   * Register detector which will be used to detect subfacets on the fly
   * @param fileType type of facet descriptor files
   * @param virtualFilePattern preliminary filter for facet descriptor file
   * @param psiFilePattern filter for facet descriptors
   * @param facetDetector detector
   * @param selector {@link UnderlyingFacetSelector} instance which will be used to select a parent facet for a detected facet
   *
   * @deprecated do not use facet detectors based on PsiFile, because it may slow down facet detection process.
   * Use {@link #registerUniversalSubFacetDetector} instead
   */
  @Deprecated
  <U extends FacetConfiguration>
  void registerOnTheFlySubFacetDetector(@NotNull FileType fileType, @NotNull VirtualFilePattern virtualFilePattern,
                                        @NotNull ElementPattern<? extends PsiFile> psiFilePattern, @NotNull FacetDetector<PsiFile, C> facetDetector,
                                        UnderlyingFacetSelector<VirtualFile, U> selector);

  /**
   * Register detector which will be used in the module wizard when a module is created from sources
   * @param fileType type of facet descriptor files
   * @param virtualFileFilter filter for facet descriptors
   * @param detector detector
   */
  void registerDetectorForWizard(@NotNull FileType fileType, @NotNull VirtualFileFilter virtualFileFilter, @NotNull FacetDetector<VirtualFile, C> detector);

  void registerDetectorForWizard(@NotNull FileType fileType, @NotNull VirtualFilePattern virtualFilePattern, @NotNull FacetDetector<VirtualFile, C> facetDetector);

  /**
   * Register detector which will be used in the module wizard when a module is created from sources
   * @param fileType type of facet descriptor files
   * @param virtualFilePattern filter for facet descriptors
   * @param facetDetector detector
   * @param underlyingFacetSelector {@link UnderlyingFacetSelector} instance which will be used to select a parent facet for a detected facet
   */
  <U extends FacetConfiguration>
  void registerSubFacetDetectorForWizard(@NotNull FileType fileType, @NotNull VirtualFilePattern virtualFilePattern,
                                         @NotNull FacetDetector<VirtualFile, C> facetDetector,
                                         @NotNull UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector);

  /**
   * Register detector to be used for both on-the-fly auto-detection and auto-detection in the module wizard
   * @param fileType type of facet descriptor files
   * @param virtualFileFilter fileter for facet descriptor files
   * @param detector
   */
  void registerUniversalDetector(@NotNull FileType fileType, @NotNull VirtualFileFilter virtualFileFilter, @NotNull FacetDetector<VirtualFile, C> detector);

  void registerUniversalDetector(@NotNull FileType fileType, @NotNull VirtualFilePattern virtualFilePattern, @NotNull FacetDetector<VirtualFile, C> facetDetector);

  /**
   * Register detector to be used for both on-the-fly auto-detection and auto-detection in the module wizard
   * @param fileType type of facet descriptor files
   * @param virtualFilePattern filter for facet descriptors
   * @param facetDetector detector
   * @param underlyingFacetSelector {@link UnderlyingFacetSelector} instance which will be used to select a parent facet for a detected facet
   */
  <U extends FacetConfiguration> void registerUniversalSubFacetDetector(@NotNull FileType fileType,
                                                                        @NotNull VirtualFilePattern virtualFilePattern,
                                                                        @NotNull FacetDetector<VirtualFile, C> facetDetector,
                                                                        UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector);
}
