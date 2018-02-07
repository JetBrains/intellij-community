/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.facet.frameworks.beans;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.List;

@Tag("item")
public class ArtifactItem {
  @Property(surroundWithTag = false)
  @XCollection
  public RequiredClass[] myRequiredClasses;
  
  @Attribute("name")
  public String myName;

  @Attribute("url")
  public String myUrl;

  @Attribute("srcUrl")
  public String mySourceUrl;

  @Attribute("docUrl")
  public String myDocUrl;

  @Attribute("md5")
  public String myMD5;

  @Attribute("optional")
  public boolean myOptional;

  public String getName() {
    return myName == null ? getNameFromUrl() : myName;
  }

  private String getNameFromUrl() {
    final int index = myUrl.lastIndexOf('/');
    return index == -1 ? myUrl : myUrl.substring(index + 1);
  }

  public boolean isOptional() {
    return myOptional;
  }

  public String getSourceUrl() {
    return mySourceUrl;
  }

  public String getDocUrl() {
    return myDocUrl;
  }

  public String getUrl() {
    return myUrl;
  }

  public String getMD5() {
    return myMD5;
  }

  @Override
  public String toString() {
    return myName;    
  }

  public String[] getRequiredClasses() {
    if (myRequiredClasses == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    
    final List<String> classes = ContainerUtil.mapNotNull(myRequiredClasses, requiredClass -> requiredClass.getFqn());

    return ArrayUtil.toStringArray(classes);
  }
}