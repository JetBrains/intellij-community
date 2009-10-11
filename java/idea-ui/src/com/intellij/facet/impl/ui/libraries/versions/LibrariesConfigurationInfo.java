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

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;

public class LibrariesConfigurationInfo {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public LibraryConfigurationInfo[] myInfos;

  @Attribute("default-version")
  public String myDefaultVersion;

  @Attribute("default-ri")
  public String myDefaultRI;

  @Attribute("default-download-url")
  public String myDefaultDownloadUrl;

  @Attribute("default-presentation-url")
  public String myDefaultPresentationUrl;

  public LibraryConfigurationInfo[] getLibraryConfigurationInfos() {
    return myInfos;
  }

  public String getDefaultVersion() {
    return myDefaultVersion;
  }

  public String getDefaultDownloadUrl() {
    return myDefaultDownloadUrl;
  }

  public String getDefaultPresentationUrl() {
    return myDefaultPresentationUrl;
  }

  public String getDefaultRI() {
    return myDefaultRI;
  }
}