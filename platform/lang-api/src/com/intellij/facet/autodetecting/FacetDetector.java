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
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetModel;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @deprecated use {@link com.intellij.framework.detection.FrameworkDetector} instead
 *
 * @author nik
 */
@Deprecated
public abstract class FacetDetector<T, C extends FacetConfiguration> {
  private final String myId;

  /**
   * @param id unique id
   */
  protected FacetDetector(@NonNls @NotNull final String id) {
    myId = id;
  }

  /**
   * @deprecated use {@link FacetDetector#FacetDetector(String)} instead
   */
  @Deprecated
  protected FacetDetector() {
    myId = getClass().getName();
  }

  public final String getId() {
    return myId;
  }

  /**
   * Inspect {@code source} and decide does it really descriptor of a new facet or it relates to one of the existent facets
   * @param source {@link com.intellij.openapi.vfs.VirtualFile} or {@link com.intellij.psi.PsiFile} instance
   * @param existentFacetConfigurations configuration of existent facet in the module
   * @return
   * <ul>
   *  <li>{@code null} if {@code source} is not facet descriptor
   *  <li>one of {@code existentFacetConfigurations} if {@code source} relates to an existent facet
   *  <li>new facet configuration instance if {@code source} is descriptor of a new facet
   * </ul>
   */
  @Nullable
  public abstract C detectFacet(T source, Collection<C> existentFacetConfigurations);

  /**
   * Called when the detected facet is accepted by user but before the facet is added to the module
   * @param facet facet
   * @param facetModel facetModule
   * @param modifiableRootModel modifiableRootModel
   */
  public void beforeFacetAdded(@NotNull Facet facet, final FacetModel facetModel, @NotNull ModifiableRootModel modifiableRootModel) {
  }

  /**
   * Called when the detected facet is accepted by user and added to the module
   * @param facet facet
   */
  public void afterFacetAdded(@NotNull Facet facet) {
  }

}
