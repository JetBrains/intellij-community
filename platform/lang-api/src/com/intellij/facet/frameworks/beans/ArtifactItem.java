/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.facet.frameworks.beans;

import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.List;

@Tag("item")
public class ArtifactItem {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
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
    
    final List<String> classes = ContainerUtil.mapNotNull(myRequiredClasses, new Function<RequiredClass, String>() {
      @Override
      public String fun(RequiredClass requiredClass) {
        return requiredClass.getFqn();
      }
    });

    return ArrayUtil.toStringArray(classes);
  }
}