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
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link com.intellij.framework.detection.FrameworkDetector} instead
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
public interface FacetDetectorRegistry<C extends FacetConfiguration> {

  /**
   * @deprecated use {@link com.intellij.framework.detection.FrameworkDetector} instead
   */
  @Deprecated
  void registerOnTheFlyDetector(@NotNull FileType fileType, @NotNull VirtualFileFilter virtualFileFilter, @NotNull Condition<PsiFile> psiFileFilter,
                                @NotNull FacetDetector<PsiFile, C> detector);
}
