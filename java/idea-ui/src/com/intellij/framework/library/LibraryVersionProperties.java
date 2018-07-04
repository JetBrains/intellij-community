// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.library;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
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
  public void loadState(@NotNull LibraryVersionProperties state) {
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
