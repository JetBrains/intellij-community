// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibraryLicenseBuilder {
  public LibraryLicense build() {
    return new LibraryLicense(name, url, version, libraryName, additionalLibraryNames, attachedTo, transitiveDependency, license,
                              licenseUrl);
  }

  public final String getName() {
    return name;
  }

  public final String getUrl() {
    return url;
  }

  public final String getVersion() {
    return version;
  }

  public final String getLibraryName() {
    return libraryName;
  }

  public final List<String> getAdditionalLibraryNames() {
    return additionalLibraryNames;
  }

  public final String getAttachedTo() {
    return attachedTo;
  }

  public final boolean getTransitiveDependency() {
    return transitiveDependency;
  }

  public final boolean isTransitiveDependency() {
    return transitiveDependency;
  }

  public final String getLicense() {
    return license;
  }

  public final String getLicenseUrl() {
    return licenseUrl;
  }

  public LibraryLicenseBuilder(String name,
                               String url,
                               String version,
                               String libraryName,
                               List<String> additionalLibraryNames,
                               String attachedTo,
                               boolean transitiveDependency,
                               String license,
                               String licenseUrl) { }

  public LibraryLicenseBuilder(Map args) { }

  public LibraryLicenseBuilder() { }

  private final String name;
  private final String url;
  private final String version;
  private final String libraryName;
  private final List<String> additionalLibraryNames = new ArrayList<String>();
  private final String attachedTo;
  private final boolean transitiveDependency;
  private final String license;
  private final String licenseUrl;
}
