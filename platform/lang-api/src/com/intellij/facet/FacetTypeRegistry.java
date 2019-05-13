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

package com.intellij.facet;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetTypeRegistry {

  public static FacetTypeRegistry getInstance() {
    return ServiceManager.getService(FacetTypeRegistry.class);
  }

  /**
   * @deprecated register {@code facetType} as an extension instead
   */
  @Deprecated
  public abstract void registerFacetType(FacetType facetType);

  /**
   * @deprecated register {@code facetType} as an extension instead
   */
  @Deprecated
  public abstract void unregisterFacetType(FacetType facetType);

  @NotNull
  public abstract FacetTypeId[] getFacetTypeIds();

  @NotNull
  public abstract FacetType[] getFacetTypes();

  @NotNull
  public abstract FacetType[] getSortedFacetTypes();

  @Nullable
  public abstract FacetType findFacetType(String id);

  @NotNull
  public abstract <F extends Facet<C>, C extends FacetConfiguration> FacetType<F, C> findFacetType(@NotNull FacetTypeId<F> typeId);
}
