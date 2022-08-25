// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

@Suppress("MemberVisibilityCanBePrivate")
class LibraryLicenseBuilder(
  var name: String? = null,
  var url: String? = null,
  var version: String? = null,
  var libraryName: String? = null,
  var additionalLibraryNames: List<String> = emptyList(),
  var attachedTo: String? = null,
  var transitiveDependency: Boolean = false,
  var license: String? = null,
  var licenseUrl: String? = null,
) {
  @Suppress("UNCHECKED_CAST")
  constructor(map: Map<String, Any>) : this(
    name = map.get("name") as String?,
    url = map.get("url") as String?,
    version = map.get("version") as String?,
    libraryName = map.get("libraryName") as String?,
    additionalLibraryNames = map.get("additionalLibraryNames") as? List<String> ?: emptyList(),
    attachedTo = map.get("attachedTo") as String?,
    transitiveDependency = map.get("transitiveDependency") == true,
    license = map.get("license") as String?,
    licenseUrl = map.get("licenseUrl") as String?,
  )

  companion object {
    fun build(builder: (b: LibraryLicenseBuilder) -> Unit): LibraryLicense {
      val b = LibraryLicenseBuilder()
      builder(b)
      return b.build()
    }
  }

  fun build(): LibraryLicense {
    return LibraryLicense(
      name = name,
      url = url,
      version = version,
      libraryName = libraryName,
      additionalLibraryNames = additionalLibraryNames,
      attachedTo = attachedTo,
      transitiveDependency = transitiveDependency,
      license = license,
      licenseUrl = licenseUrl
    )
  }
}