// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.containers.ContainerUtil
import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * Describes a library which is included into distribution of an IntelliJ-based IDE. This information is used to show list of Third-party
 * software in About popup and on the product download page.
 */
@CompileStatic
@Immutable
class LibraryLicense {
  private static final Map<String, String> PREDEFINED_LICENSE_URLS = ["Apache 2.0": "http://www.apache.org/licenses/LICENSE-2.0"]
  public static final String JETBRAINS_OWN = "JetBrains"

  /**
   * Presentable full name of the library. If {@code null} {@link #libraryName} will be used instead.
   */
  final String name

  /**
   * URL of the library site, may be {@code null} if the library doesn't have a site
   */
  final String url

  /**
   * Version of the library. If {@link #libraryName} points to a Maven library version is taken from the library configuration so it must
   * not be specified explicitly.
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
   * Set to {@code true} if this entry describes license for a transitive dependency included into the library specified by {@link #libraryName}
   */
  final boolean transitiveDependency

  /**
   * Type of a license (e.g. {@code 'Apache 2.0'})
   */
  final String license

  /**
   * URL of a page on the library site (or a generic site) containing the license text, may be {@code null} for standard licenses
   * (see {@link #PREDEFINED_LICENSE_URLS}) or if there is no such page.
   */
  final String licenseUrl

  /**
   * Use this method only for JetBrains's own libraries which are available as part of IntelliJ-based IDEs only so there is no way to
   * give link to their sites. For other libraries please fill all necessary fields of {@link LibraryLicense} instead of using this method.
   */
  static jetbrainsLibrary(String libraryName) {
    new LibraryLicense(libraryName: libraryName, license: JETBRAINS_OWN)
  }

  List<String> getLibraryNames() {
    return ContainerUtil.createMaybeSingletonList(libraryName) + (additionalLibraryNames ?: [] as List<String>)
  }

  String getPresentableName() {
    name ?: libraryName
  }

  String getLibraryLicenseUrl() {
    licenseUrl ?: PREDEFINED_LICENSE_URLS[license]
  }
}