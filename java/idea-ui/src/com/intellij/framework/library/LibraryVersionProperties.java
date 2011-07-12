/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.library;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
* @author nik
*/
public class LibraryVersionProperties extends LibraryProperties<LibraryVersionProperties> {
  private String myVersionString;

  public LibraryVersionProperties() {
  }

  public LibraryVersionProperties(@Nullable String versionString) {
    myVersionString = versionString;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LibraryVersionProperties && Comparing.equal(myVersionString, ((LibraryVersionProperties)obj).myVersionString);
  }

  @Override
  public int hashCode() {
    return Comparing.hashcode(myVersionString);
  }

  @Override
  public LibraryVersionProperties getState() {
    return this;
  }

  @Override
  public void loadState(LibraryVersionProperties state) {
    myVersionString = state.myVersionString;
  }

  @Attribute("version")
  public String getVersionString() {
    return myVersionString;
  }

  public void setVersionString(String version) {
    myVersionString = version;
  }
}
