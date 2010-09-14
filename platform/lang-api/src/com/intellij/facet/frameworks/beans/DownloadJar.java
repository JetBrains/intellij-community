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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.List;

@Tag("jar")
public class DownloadJar {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public RequiredClass[] myRequiredClasses;
  
  @Attribute("name")
  public String myName;

  @Attribute("url")
  public String myDownloadUrl;

  @Attribute("presentation")
  public String myPresentation;

  @Attribute("md5")
  public String myMD5;

  public String getName() {
    return myName;
  }

  public String getDownloadUrl() {
    return myDownloadUrl;
  }

  public String getPresentation() {
    return myPresentation;
  }

  public String getMD5() {
    return myMD5;
  }

  @Override
  public String toString() {
    return myName;    
  }

  public String[] getRequiredClasses() {
    if (myRequiredClasses == null) return new String[0];
    
    final List<String> classes = ContainerUtil.mapNotNull(myRequiredClasses, new Function<RequiredClass, String>() {
      @Override
      public String fun(RequiredClass requiredClass) {
        return requiredClass.getFqn();
      }
    });
    
    return classes.toArray(new String[classes.size()]);
  }
}