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
package org.jetbrains.jps.model.serialization.facet;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.module.JpsModule;

public abstract class JpsFacetConfigurationSerializer<E extends JpsElement> {
  private final JpsElementChildRole<E> myRole;
  private final String myFacetTypeId;
  private final String myFacetName;

  public JpsFacetConfigurationSerializer(JpsElementChildRole<E> role, String facetTypeId, final @Nullable String facetName) {
    myRole = role;
    myFacetTypeId = facetTypeId;
    myFacetName = facetName;
  }

  public String getFacetTypeId() {
    return myFacetTypeId;
  }

  public E loadExtension(final Element configurationElement, final String facetName, JpsModule module, JpsElement parentFacet) {
    final E e = loadExtension(configurationElement, facetName, parentFacet, module);
    return module.getContainer().setChild(myRole, e);
  }

  protected abstract E loadExtension(@NotNull Element facetConfigurationElement, String name, JpsElement parent, JpsModule module);

  public boolean hasExtension(JpsModule module) {
    return module.getContainer().getChild(myRole) != null;
  }

  /**
   * @deprecated the build process doesn't save project configuration so there is no need to implement this method, it isn't called by the platform
   */
  @Deprecated
  protected void saveExtension(E extension, Element facetConfigurationTag, JpsModule module) {
  }
}
