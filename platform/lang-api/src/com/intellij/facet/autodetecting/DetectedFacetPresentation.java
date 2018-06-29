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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link com.intellij.framework.detection.FrameworkDetector} instead
 *
 * @author nik
 */
@Deprecated
public abstract class DetectedFacetPresentation {

  @Nullable
  public String getDetectedFacetDescription(VirtualFile root, VirtualFile[] files) {
    return null;
  }

  @Nullable
  public String getAutodetectionPopupText(@NotNull Module module, @NotNull FacetType facetType, @NotNull String facetName, @NotNull VirtualFile[] files) {
    return null;
  }


  /**
   * @deprecated override {@link DetectedFacetPresentation#getAutodetectionPopupText(com.intellij.openapi.module.Module, com.intellij.facet.FacetType, String, com.intellij.openapi.vfs.VirtualFile[])}
   * instead
   */
  @Deprecated
  @Nullable
  public String getAutodetectionPopupText(@NotNull Facet facet, @NotNull VirtualFile[] files) {
    return null;
  }

}
