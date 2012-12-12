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

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
*/
@Tag(JpsFacetSerializer.FACET_TAG)
public class FacetState {
  private String myFacetType;
  private String myName;
  private Element myConfiguration;
  private List<FacetState> mySubFacets = new ArrayList<FacetState>();

  @Attribute(JpsFacetSerializer.TYPE_ATTRIBUTE)
  public String getFacetType() {
    return myFacetType;
  }

  @Attribute(JpsFacetSerializer.NAME_ATTRIBUTE)
  public String getName() {
    return myName;
  }

  @Tag(JpsFacetSerializer.CONFIGURATION_TAG)
  public Element getConfiguration() {
    return myConfiguration;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<FacetState> getSubFacets() {
    return mySubFacets;
  }

  public void setSubFacets(final List<FacetState> subFacets) {
    mySubFacets = subFacets;
  }

  public void setConfiguration(final Element configuration) {
    myConfiguration = configuration;
  }

  public void setName(final String name) {
    myName = name;
  }

  public void setFacetType(final String type) {
    myFacetType = type;
  }
}
