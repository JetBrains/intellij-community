// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class LibraryLicenseBuilder {
  final String name
  final String url
  final String version
  final String libraryName
  final List<String> additionalLibraryNames = []
  final String attachedTo
  final boolean transitiveDependency
  final String license
  final String licenseUrl

  LibraryLicense build() {
    return new LibraryLicense(
      name, url, version, libraryName, additionalLibraryNames,
      attachedTo, transitiveDependency, license, licenseUrl
    )
  }
}
