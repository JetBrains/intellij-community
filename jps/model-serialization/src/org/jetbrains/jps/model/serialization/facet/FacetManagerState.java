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

package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
*/
public class FacetManagerState {
  private List<FacetState> myFacets = new ArrayList<FacetState>();

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<FacetState> getFacets() {
    return myFacets;
  }

  public void setFacets(final List<FacetState> facets) {
    myFacets = facets;
  }
}
