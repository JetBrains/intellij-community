// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
final class LibraryLicense {
  private static final String APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
  private static final Map<String, String> PREDEFINED_LICENSE_URLS = ["Apache 2.0": APACHE_LICENSE_URL]
  public static final String JETBRAINS_OWN = "JetBrains"
  /**
   * Denotes version of library built from custom revision
   */
  public static final String CUSTOM_REVISION = "custom revision"

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

  LibraryLicense apache() {
    assert license == null: "No need to specify 'license' for Apache 2.0"
    assert !licenseUrl?.contains("apache.org/licenses"): "No need to specify default 'licenseUrl' for Apache 2.0"
    new LibraryLicense(
      name: name,
      url: url,
      version: version,
      libraryName: libraryName,
      additionalLibraryNames: additionalLibraryNames,
      attachedTo: attachedTo,
      transitiveDependency: transitiveDependency,
      license: "Apache 2.0",
      licenseUrl: licenseUrl ?: APACHE_LICENSE_URL
    )
  }

  LibraryLicense simplifiedBsd() {
    assert license == null: "No need to specify 'license' for Simplified BSD"
    assert !licenseUrl?.contains("opensource.org/licenses"): "No need to specify default 'licenseUrl' for Simplified BSD"
    new LibraryLicense(
      name: name,
      url: url,
      version: version,
      libraryName: libraryName,
      additionalLibraryNames: additionalLibraryNames,
      attachedTo: attachedTo,
      transitiveDependency: transitiveDependency,
      license: "Simplified BSD",
      licenseUrl: licenseUrl ?: "https://opensource.org/licenses/BSD-2-Clause"
    )
  }

  LibraryLicense newBsd() {
    assert license == null: "No need to specify 'license' for New BSD"
    assert !licenseUrl?.contains("opensource.org/licenses"): "No need to specify default 'licenseUrl' for New BSD"
    new LibraryLicense(
      name: name,
      url: url,
      version: version,
      libraryName: libraryName,
      additionalLibraryNames: additionalLibraryNames,
      attachedTo: attachedTo,
      transitiveDependency: transitiveDependency,
      license: "New BSD",
      licenseUrl: licenseUrl ?: "https://opensource.org/licenses/BSD-3-Clause"
    )
  }

  LibraryLicense mit() {
    assert license == null: "No need to specify 'license' for MIT"
    assert !licenseUrl?.contains("opensource.org/licenses"): "No need to specify default 'licenseUrl' for MIT"
    new LibraryLicense(
      name: name,
      url: url,
      version: version,
      libraryName: libraryName,
      additionalLibraryNames: additionalLibraryNames,
      attachedTo: attachedTo,
      transitiveDependency: transitiveDependency,
      license: "MIT",
      licenseUrl: licenseUrl ?: "https://opensource.org/licenses/MIT"
    )
  }

  LibraryLicense eplV1() {
    epl(1)
  }

  LibraryLicense eplV2() {
    epl(2)
  }

  private LibraryLicense epl(int v) {
    assert v == 1 || v == 2: "Version must be either 1 or 2 for Eclipse Public License"
    assert license == null: "No need to specify 'license' for Eclipse Public License"
    assert !licenseUrl?.contains("eclipse.org"): "No need to specify default 'licenseUrl' for Eclipse Public License"
    new LibraryLicense(
      name: name,
      url: url,
      version: version,
      libraryName: libraryName,
      additionalLibraryNames: additionalLibraryNames,
      attachedTo: attachedTo,
      transitiveDependency: transitiveDependency,
      license: "Eclipse Public License $v.0",
      licenseUrl: licenseUrl ?: (v == 1 ? "https://www.eclipse.org/org/documents/epl-v10.html" : "https://www.eclipse.org/legal/epl-2.0")
    )
  }
}