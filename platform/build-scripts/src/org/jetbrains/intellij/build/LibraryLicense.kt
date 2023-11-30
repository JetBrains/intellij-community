// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.LibraryLicense.Companion.PREDEFINED_LICENSE_URLS

/**
 * Describes a library which is included into distribution of an IntelliJ-based IDE. This information is used to show list of Third-party
 * software in About popup and on the product download page.
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
   * is used to automatically generate list of libraries used in a particular project.
   */
  val libraryName: String? = null,

  /**
   * Names of additional libraries in IDEA Project Configuration which are associated with the given library. It makes sense to use this
   * property only for unnamed module library consisting of several JAR files, but even in such cases consider merging such JARs into a single
   * module library and giving it a name.
   */
  val additionalLibraryNames: List<String> = emptyList(),

  /**
   * Specifies name of the module in IDEA Project configuration the library is implicitly attached to. It makes sense to use this property
   * only for libraries which cannot be added to a module dependencies as a regular dependency (e.g. if it isn't a Java library). For regular
   * cases specify [libraryName] instead.
   */
  val attachedTo: String? = null,

  /**
   * Set to `true` if this entry describes license for a transitive dependency included into the library specified by [libraryName]
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
) {
  /**
   * See [org.spdx.library.SpdxConstants.LISTED_LICENSE_URL]
   */
  @Internal
  var spdxIdentifier: String? = null

  /**
   * See:
   * * https://spdx.github.io/spdx-spec/v2.3/package-information/#75-package-supplier-field
   * * https://package-search.jetbrains.com/
   */
  internal var supplier: String? = null
    set(value) {
      require(value == null || value.startsWith("Organization: ") || value.startsWith("Person: ")) {
        "$value should start either with 'Organization: ' or with 'Person: ' prefix"
      }
      field = value
    }

  @Internal
  fun suppliedByOrganizations(vararg organizations: String): LibraryLicense {
    require(organizations.any())
    supplier = organizations.joinToString(prefix = "Organization: ")
    return this
  }

  @Internal
  fun suppliedByPersons(vararg persons: String): LibraryLicense {
    require(persons.any())
    supplier = persons.joinToString(prefix = "Person: ")
    return this
  }

  init {
    require(name != null || libraryName != null) { "name or libraryName must be set" }
  }

  companion object {
    private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
    private val PREDEFINED_LICENSE_URLS = mapOf("Apache 2.0" to APACHE_LICENSE_URL)

    @JvmStatic
    val JETBRAINS_OWN = "JetBrains"

    /**
     * Denotes version of library built from custom revision
     */
    const val CUSTOM_REVISION = "custom revision"

    /**
     * Use this method only for JetBrains own libraries which are available as part of IntelliJ-based IDEs only so there is no way to
     * give link to their sites. For other libraries please fill all necessary fields of [LibraryLicense] instead of using this method.
     */
    @JvmStatic
    fun jetbrainsLibrary(libraryName: String): LibraryLicense {
      return LibraryLicense(
        libraryName = libraryName,
        license = JETBRAINS_OWN,
      )
    }
  }

  fun getLibraryNames(): List<String> = listOfNotNull(libraryName) + additionalLibraryNames

  val presentableName: String
    get() = name ?: libraryName!!

  fun getLibraryLicenseUrl(): String? = licenseUrl ?: PREDEFINED_LICENSE_URLS.get(license)

  @Deprecated("Please specify exact URL for the Apache license, pointing to the repo of the dependency")
  fun apache(): LibraryLicense {
    require(license == null) { "No need to specify 'license' for Apache 2.0" }
    require(licenseUrl?.contains("apache.org/licenses") != true) { "No need to specify default 'licenseUrl' for Apache 2.0" }
    return copy(
      license = "Apache 2.0",
      licenseUrl = licenseUrl ?: APACHE_LICENSE_URL,
    ).apply {
      spdxIdentifier = "Apache-2.0"
    }
  }

  fun apache(licenseUrl: String): LibraryLicense {
    require(license == null) { "No need to specify 'license' for Apache 2.0" }
    return copy(
      license = "Apache 2.0",
      licenseUrl = licenseUrl
    ).apply {
      spdxIdentifier = "Apache-2.0"
    }
  }

  @Deprecated("Please specify exact URL for the BSD license, pointing to the repo of the dependency")
  fun simplifiedBsd(): LibraryLicense {
    require(license == null) { "No need to specify 'license' for Simplified BSD" }
    require(licenseUrl?.contains("opensource.org/licenses") != true) { "No need to specify default 'licenseUrl' for Simplified BSD" }
    return copy(
      license = "BSD 2-Clause",
      licenseUrl = licenseUrl ?: "https://opensource.org/licenses/BSD-2-Clause",
    ).apply {
      spdxIdentifier = "BSD-2-Clause"
    }
  }

  fun simplifiedBsd(licenseUrl: String): LibraryLicense {
    require(license == null) { "No need to specify 'license' for Simplified BSD" }
    return copy(
      license = "BSD 2-Clause",
      licenseUrl = licenseUrl
    ).apply {
      spdxIdentifier = "BSD-2-Clause"
    }
  }
  @Deprecated("Please specify exact URL for the BSD license, pointing to the repo or Web site of the dependency")
  fun newBsd(): LibraryLicense {
    require(license == null) { "No need to specify 'license' for New BSD" }
    require(licenseUrl?.contains("opensource.org/licenses") != true) { "No need to specify default 'licenseUrl' for New BSD" }
    return copy(
      license = "BSD 3-Clause",
      licenseUrl = licenseUrl ?: "https://opensource.org/licenses/BSD-3-Clause",
    ).apply {
      spdxIdentifier = "BSD-3-Clause"
    }
  }

  fun newBsd(licenseUrl: String): LibraryLicense {
    require(license == null) { "No need to specify 'license' for New BSD" }
    return copy(
      license = "BSD 3-Clause",
      licenseUrl = licenseUrl
    ).apply {
      spdxIdentifier = "BSD-3-Clause"
    }
  }

  @Deprecated("Please specify exact URL for the MIT license, pointing to the repo or Web site of the dependency")
  fun mit(): LibraryLicense {
    require(license == null) { "No need to specify 'license' for MIT" }
    require(licenseUrl?.contains("opensource.org/licenses") != true) { "No need to specify default 'licenseUrl' for MIT" }
    return copy(
      license = "MIT",
      licenseUrl = licenseUrl ?: "https://opensource.org/licenses/MIT",
    ).apply {
      spdxIdentifier = "MIT"
    }
  }

  fun mit(licenseUrl: String): LibraryLicense {
    require(license == null) { "No need to specify 'license' for MIT" }
    return copy(
      license = "MIT",
      licenseUrl = licenseUrl
    ).apply {
      spdxIdentifier = "MIT"
    }
  }

  fun eplV1(): LibraryLicense = epl(1)
  fun eplV2(): LibraryLicense = epl(2)
  fun eplV1(licenseUrl: String): LibraryLicense = epl(licenseUrl, 1)
  fun eplV2(licenseUrl: String): LibraryLicense = epl(licenseUrl, 2)

  private fun epl(licenseUrl: String, v: Int): LibraryLicense {
    require(license == null) { "No need to specify 'license' for EPL" }
    return copy(
      license = "Eclipse Public License ${v}.0",
      licenseUrl = licenseUrl
    ).apply {
      spdxIdentifier = "EPL-$v.0"
    }
  }

  private fun epl(v: Int): LibraryLicense {
    require(v == 1 || v == 2) { "Version must be either 1 or 2 for Eclipse Public License" }
    require(license == null) { "No need to specify 'license' for Eclipse Public License" }
    require(licenseUrl?.contains("eclipse.org") != true) { "No need to specify default 'licenseUrl' for Eclipse Public License" }
    return copy(
      license = "Eclipse Public License ${v}.0",
      licenseUrl = licenseUrl
                   ?: (if (v == 1) "https://www.eclipse.org/org/documents/epl-v10.html" else "https://www.eclipse.org/legal/epl-2.0")
    ).apply {
      spdxIdentifier = "EPL-$v.0"
    }
  }

  internal var forkedFrom: LibraryUpstream? = null

  /**
   * See [org.jetbrains.intellij.build.LibraryUpstream]
   */
  @Internal
  fun forkedFrom(groupId: String, artifactId: String,
                 version: String? = null, revision: String? = null,
                 mavenRepositoryUrl: String? = null,
                 sourceCodeUrl: String? = null,
                 authors: String? = null): LibraryLicense {
    return copy().apply {
      forkedFrom = LibraryUpstream(
        mavenRepositoryUrl = mavenRepositoryUrl,
        sourceCodeUrl = sourceCodeUrl,
        groupId = groupId, artifactId = artifactId,
        version = version, revision = revision,
        LibraryLicense(
          libraryName = "$groupId:$artifactId",
          version = version, url = sourceCodeUrl,
          licenseUrl = this.licenseUrl,
          license = this.license,
        ).let {
          it.suppliedByPersons(authors ?: return@let it)
        }
      )
    }
  }
}
