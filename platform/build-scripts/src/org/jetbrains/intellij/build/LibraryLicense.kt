// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.LibraryLicense.Companion.PREDEFINED_LICENSE_URLS

/**
 * Describes a library which is included in the distribution of an IntelliJ-based IDE.
 * This information is used to show a list of third-party software in the _About_ popup and on the product download page.
 */
data class LibraryLicense(
  /**
   * Presentable full name of the library. If `null` [libraryName] will be used instead.
   */
  val name: String? = null,

  /**
   * URL of the library site, may be `null` if the library doesn't have a site
   */
  val url: String? = null,

  /**
   * Version of the library. If [libraryName] points to a Maven library version is taken from the library configuration, so it must
   * not be specified explicitly.
   */
  val version: String? = null,

  /**
   * Name of the library in IDEA Project Configuration (for unnamed module libraries it's a name of its JAR file). May be `null` if
   * the library isn't explicitly added to dependencies of a module, in that case [attachedTo] must be specified instead. This property
   * is used to automatically generate a list of libraries used in a particular project.
   */
  val libraryName: String? = null,

  /**
   * Names of additional libraries in IDEA Project Configuration which are associated with the given library. It makes sense to use this
   * property only for an unnamed module library consisting of several JAR files, but even in such cases, consider merging such JARs into a single
   * module library and giving it a name.
   */
  val additionalLibraryNames: List<String> = emptyList(),

  /**
   * Specifies the name of the module in IDEA Project configuration the library is implicitly attached to.
   * It makes sense to use this property only for libraries which cannot be added to a module dependencies as a regular dependency
   * (e.g., if it isn't a Java library).
   * For regular cases specify [libraryName] instead.
   */
  val attachedTo: String? = null,

  /**
   * Set to `true` if this entry describes license for a transitive dependency included in the library specified by [libraryName]
   */
  val transitiveDependency: Boolean = false,

  /**
   * Type of license (e.g. `"Apache 2.0"`)
   */
  val license: String? = null,

  /**
   * URL of a page on the library site (or a generic site) containing the license text, may be `null` for standard licenses
   * (see [PREDEFINED_LICENSE_URLS]) or if there is no such page.
   */
  val licenseUrl: String? = null,

  /**
   * See [SPDX License List](https://spdx.org/licenses/).
   */
  val spdxIdentifier: String? = null,

  /**
   * See:
   * * https://spdx.github.io/spdx-spec/v2.3/package-information/#75-package-supplier-field
   * * https://package-search.jetbrains.com/
   */
  val supplier: String? = null,

  /**
   * See [LibraryUpstream]
   */
  var forkedFrom: LibraryUpstream? = null,

  /**
   * [Specification](https://spdx.github.io/spdx-spec/v2.3/package-information/#717-copyright-text-field)
   */
  val copyrightText: String? = null,
) {
  init {
    require(name != null || libraryName != null) { "name or libraryName must be set" }
    require(supplier == null || supplier.startsWith("Organization: ") || supplier.startsWith("Person: ")) {
      "$supplier should start either with 'Organization: ' or with 'Person: ' prefix"
    }
    require(license != JETBRAINS_OWN || licenseUrl == null && spdxIdentifier == null) {
      "Library '${name ?: libraryName}' has $JETBRAINS_OWN license and " +
      "is not expected to have neither 'licenseUrl=$licenseUrl' nor 'spdxIdentifier=$spdxIdentifier' specified"
    }
  }

  companion object {
    private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
    private val PREDEFINED_LICENSE_URLS = mapOf("Apache 2.0" to APACHE_LICENSE_URL)

    const val JETBRAINS_OWN = "JetBrains"

    /**
     * Denotes the version of a library built from custom revision.
     */
    const val CUSTOM_REVISION = "custom revision"

    /**
     * Use this method only for JetBrains own libraries which are available as part of IntelliJ-based IDEs only,
     * so there is no way to give a link to their sites.
     * For other libraries please fill all necessary fields of [LibraryLicense] instead of using this method.
     */
    fun jetbrainsLibrary(libraryName: String): LibraryLicense = LibraryLicense(libraryName = libraryName, license = JETBRAINS_OWN)
  }

  fun additionalLibraryNames(vararg names: String): LibraryLicense {
    require(names.any())
    return copy(additionalLibraryNames = names.toList())
  }

  fun suppliedByOrganizations(vararg organizations: String): LibraryLicense {
    require(organizations.any())
    return copy(supplier = organizations.joinToString(prefix = "Organization: "))
  }

  fun suppliedByPersons(vararg persons: String): LibraryLicense {
    require(persons.any())
    return copy(supplier = persons.joinToString(prefix = "Person: "))
  }

  fun getLibraryNames(): List<String> = listOfNotNull(libraryName) + additionalLibraryNames

  val presentableName: String
    get() = name ?: libraryName!!

  fun getLibraryLicenseUrl(): String? = licenseUrl ?: PREDEFINED_LICENSE_URLS[license]

  fun license(license: String, licenseUrl: String?, spdxIdentifier: String? = null): LibraryLicense {
    require(this.license == null && this.licenseUrl == null) { "License is already defined" }
    return copy(license = license, licenseUrl = licenseUrl, spdxIdentifier = spdxIdentifier)
  }

  fun apache(licenseUrl: String): LibraryLicense = license("Apache 2.0", licenseUrl, spdxIdentifier = "Apache-2.0")

  fun simplifiedBsd(licenseUrl: String): LibraryLicense = license("BSD 2-Clause", licenseUrl, spdxIdentifier = "BSD-2-Clause")

  fun newBsd(licenseUrl: String): LibraryLicense = license("BSD 3-Clause", licenseUrl, spdxIdentifier = "BSD-3-Clause")

  fun mit(licenseUrl: String): LibraryLicense = license("MIT", licenseUrl, spdxIdentifier = "MIT")

  fun eplV1(licenseUrl: String): LibraryLicense = license("EPL 1.0", licenseUrl, spdxIdentifier = "EPL-1.0")

  fun eplV2(licenseUrl: String): LibraryLicense = license("EPL 2.0", licenseUrl, spdxIdentifier = "EPL-2.0")

  fun gpl2ce(licenseUrl: String): LibraryLicense = license("GPL 2.0 + Classpath", licenseUrl, spdxIdentifier = "GPL-2.0 WITH Classpath-exception-2.0")

  fun lgpl2(licenseUrl: String): LibraryLicense = license("LGPL 2.0", licenseUrl, spdxIdentifier = "LGPL-2.0-only")

  fun lgpl21(licenseUrl: String): LibraryLicense = license("LGPL 2.1", licenseUrl, spdxIdentifier = "LGPL-2.1-only")

  fun lgpl21plus(licenseUrl: String): LibraryLicense = license("LGPL 2.1+", licenseUrl, spdxIdentifier = "LGPL-2.1-or-later")

  fun lgpl3(licenseUrl: String): LibraryLicense = license("LGPL 3.0", licenseUrl, spdxIdentifier = "LGPL-3.0-only")

  fun cddl11(licenseUrl: String): LibraryLicense = license("CDDL 1.1", licenseUrl, spdxIdentifier = "CDDL-1.1")

  fun mpl2(licenseUrl: String): LibraryLicense = license("MPL 2.0", licenseUrl, spdxIdentifier = "MPL-2.0")

  fun upl(licenseUrl: String): LibraryLicense = license("UPL 1.0", licenseUrl, spdxIdentifier = "UPL-1.0")

  fun public(licenseUrl: String): LibraryLicense = license("Public Domain (CC0)", licenseUrl, spdxIdentifier = "CC0-1.0")

  /**
   * See [org.jetbrains.intellij.build.LibraryUpstream]
   */
  fun forkedFrom(
    groupId: String,
    artifactId: String,
    version: String? = null,
    revision: String? = null,
    mavenRepositoryUrl: String? = null,
    sourceCodeUrl: String? = null,
    authors: String? = null,
  ): LibraryLicense = copy(
    forkedFrom = LibraryUpstream(mavenRepositoryUrl, sourceCodeUrl, groupId, artifactId, version, revision, LibraryLicense(
      libraryName = "${groupId}:${artifactId}",
      version = version,
      url = sourceCodeUrl,
      license = license,
      licenseUrl = licenseUrl
    ).let {
      it.suppliedByPersons(authors ?: return@let it)
    })
  )

  fun copyrightText(value: String): LibraryLicense = copy(copyrightText = value)
}
