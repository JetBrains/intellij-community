// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.jps.model.serialization.facet;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.jps.model.serialization.SerializationConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Tag(JpsFacetSerializer.FACET_TAG)
public final class FacetState {
  private String myFacetType;
  private String myName;
  private String myExternalSystemId;
  private String myExternalSystemIdInInternalStorage;
  private Element myConfiguration;

  @Property(surroundWithTag = false)
  @XCollection
  public final List<FacetState> subFacets = new ArrayList<>();

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

  @Attribute(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE)
  public String getExternalSystemId() {
    return myExternalSystemId;
  }

  @Attribute(SerializationConstants.EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE)
  public String getExternalSystemIdInInternalStorage() {
    return myExternalSystemIdInInternalStorage;
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

  public void setExternalSystemIdInInternalStorage(String externalSystemIdInInternalStorage) {
    myExternalSystemIdInInternalStorage = externalSystemIdInInternalStorage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FacetState state = (FacetState)o;
    return Objects.equals(myFacetType, state.myFacetType) &&
           Objects.equals(myName, state.myName) &&
           Objects.equals(myExternalSystemId, state.myExternalSystemId) &&
           JDOMUtil.areElementsEqual(myConfiguration, state.myConfiguration) &&
           Objects.equals(subFacets, state.subFacets);
  }

  @Override
  public int hashCode() {
    return (31 * Objects.hash(myFacetType, myName, myExternalSystemId, subFacets)) + JDOMUtil.hashCode(myConfiguration, false);
  }
}
