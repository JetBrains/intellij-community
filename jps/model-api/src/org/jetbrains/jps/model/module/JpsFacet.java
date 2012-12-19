/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;

/**
 * @author nik
 */
//todo[nik] I'm not sure that we really need separate interface for facets in the project model.
//Perhaps facets should be replaced by extensions for module elements
public interface JpsFacet extends JpsNamedElement, JpsReferenceableElement<JpsFacet> {

  JpsModule getModule();

  @NotNull
  JpsFacetType<?> getType();

  void delete();

  @NotNull
  @Override
  JpsFacetReference createReference();

  void setParentFacet(@NotNull JpsFacet facet);

  @Nullable
  JpsFacet getParentFacet();
}
