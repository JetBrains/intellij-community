/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
*/
@Tag(JpsFacetSerializer.FACET_TAG)
public class FacetState {
  private String myFacetType;
  private String myName;
  private String myExternalSystemId;
  private Element myConfiguration;
  private List<FacetState> mySubFacets = new ArrayList<>();

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

  @Attribute("external-system-id")
  public String getExternalSystemId() {
    return myExternalSystemId;
  }

  @Property(surroundWithTag = false)
  @XCollection
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

  public void setExternalSystemId(String externalSystemId) {
    myExternalSystemId = externalSystemId;
  }
}
