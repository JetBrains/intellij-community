// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus

interface SoftwareBillOfMaterials {
  /**
   * Generates Software Bill Of Materials (SBOM) for each distribution file in [SPDX format](https://spdx.github.io/spdx-spec)
   */
  suspend fun generate()

  companion object {
    const val STEP_ID: String = "sbom"

    @ApiStatus.Internal
    object Suppliers {
      const val JETBRAINS: String = "JetBrains s.r.o."
      const val GOOGLE: String = "Google LLC"
      const val APACHE: String = "The Apache Software Foundation"
      const val ECLIPSE: String = "Eclipse Foundation"
    }
  }

  class Options {
    /**
     * [Specification](https://spdx.github.io/spdx-spec/v2.3/document-creation-information/#68-creator-field)
     */
    var creator: String? = null

    /**
     * [Specification](https://spdx.github.io/spdx-spec/v2.3/package-information/#715-declared-license-field)
     */
    var license: DistributionLicense? = null

    /**
     * Used to construct SPDX document URI: [documentNamespace]/[org.spdx.library.Version]/$distributionFileName.spdx
     */
    var documentNamespace: String? = null

    /**
     * @param copyrightText https://spdx.github.io/spdx-spec/v2.3/package-information/#717-copyright-text-field
     */
    class DistributionLicense(val name: String, val text: String, val url: String?, var copyrightText: String) {
      internal companion object {
        val JETBRAINS = DistributionLicense(
          name = LibraryLicense.JETBRAINS_OWN,
          text = LibraryLicense.JETBRAINS_OWN,
          copyrightText = LibraryLicense.JETBRAINS_OWN,
          url = null,
        )
      }
    }
  }
}