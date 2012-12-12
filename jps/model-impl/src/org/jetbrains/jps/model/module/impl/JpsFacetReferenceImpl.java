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
package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceImpl;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetReference;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsFacetReferenceImpl extends JpsNamedElementReferenceImpl<JpsFacet, JpsFacetReferenceImpl> implements JpsFacetReference {
  public JpsFacetReferenceImpl(String facetName, JpsModuleReference moduleReference) {
    super(JpsFacetRole.COLLECTION_ROLE, facetName, moduleReference);
  }

  private JpsFacetReferenceImpl(JpsFacetReferenceImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsFacetReferenceImpl createCopy() {
    return new JpsFacetReferenceImpl(this);
  }
}
