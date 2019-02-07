// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.containers.ContainerUtil
import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * @author nik
 */
@CompileStatic
@Immutable
class LibraryLicense {
  private static final Map<String, String> PREDEFINED_LICENSE_URLS = ["Apache 2.0": "http://www.apache.org/licenses/LICENSE-2.0"]
  public static final String JETBRAINS_OWN = "JetBrains"

  /**
   * Presentable full name of the library
   */
  final String name

  /**
   * URL of the library site, may be {@code null} if the library doesn't have a site
   */
  final String url

  /**
   * Version of the library
   */
  final String version

  /**
   * Name of the library in IDEA Project Configuration (for unnamed module libraries it's a name of its JAR file). May be {@code null} if
   * the library isn't explicitly added to dependencies of a module, in that case {@link #attachedTo} must be specified instead. This property
   * is used to automatically generate list of libraries used in a particular project.
   */
  final String libraryName

  /**
   * Names of additional libraries in IDEA Project Configuration which are associated with the given library. It makes sense to use this
   * property only for unnamed module library consisting of several JAR files, but even in such cases consider merging such JARs into a single
   * module library and giving it a name.
   */
  final List<String> additionalLibraryNames

  /**
   * Specifies name of the module in IDEA Project configuration the library is implicitly attached to. It makes sense to use this property
   * only for libraries which cannot be added to a module dependencies as a regular dependency (e.g. if it isn't a Java library). For regular
   * cases specify {@link #libraryName} instead.
   */
  final String attachedTo

  /**
   * Type of a license (e.g. {@code 'Apache 2.0'})
   */
  final String license

  /**
   * URL of a page on the library site (or a generic site) containing the license text, may be {@code null} if there is no such page.
   */
  final String licenseUrl

  static jetbrainsLibrary(String libraryName) {
    new LibraryLicense(name: libraryName, license: JETBRAINS_OWN)
  }

  List<String> getLibraryNames() {
    return ContainerUtil.createMaybeSingletonList(libraryName ?: name) + (additionalLibraryNames ?: [] as List<String>)
  }

  String getLibraryLicenseUrl() {
    licenseUrl ?: PREDEFINED_LICENSE_URLS[license]
  }
}