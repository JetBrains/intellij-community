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

package com.intellij.facet.impl.ui.libraries.versions;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("lib")
public class LibraryConfigurationInfo {

  @Attribute("ri")
  public String myRI;

  @Attribute("jar-name")
  public String myJarName;

  @Attribute("version")
  public String myVersion;

  @Attribute("download-url")
  public String myDownloadUrl;

  @Attribute("presentation-url")
  public String myPresentationdUrl;

  @Attribute("required-classes")
  public String myRequiredClasses;

  @Attribute("jar-version")
  public String myJarVersion;

  public String getRI() {
    return myRI;
  }

  public String getJarName() {
    return myJarName;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getDownloadUrl() {
    return myDownloadUrl;
  }

  public String getPresentationdUrl() {
    return myPresentationdUrl;
  }

  public String getRequiredClasses() {
    return myRequiredClasses;
  }

  public String getJarVersion() {
    return myJarVersion;
  }
}