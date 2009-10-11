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

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.UnderlyingFacetSelector;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.ProjectFacetInfoSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class FacetByVirtualFileDetectorWrapper<C extends FacetConfiguration, F extends Facet<C>, U extends FacetConfiguration> extends FacetDetectorWrapper<VirtualFile, C, F, U> {
  public FacetByVirtualFileDetectorWrapper(ProjectFacetInfoSet projectFacetSet, FacetType<F, C> facetType,
                                           final AutodetectionFilter autodetectionFilter, final VirtualFileFilter virtualFileFilter,
                                           final FacetDetector<VirtualFile, C> facetDetector,
                                           final UnderlyingFacetSelector<VirtualFile, U> selector) {
    super(projectFacetSet, facetType, autodetectionFilter, virtualFileFilter, facetDetector, selector);
  }

  @Nullable
  public FacetInfo2<Module> detectFacet(final VirtualFile virtualFile, final PsiManager psiManager) {
    Module module = ModuleUtil.findModuleForFile(virtualFile, psiManager.getProject());
    if (module == null) {
      return null;
    }
    return detectFacet(module, virtualFile, virtualFile);
  }
}
