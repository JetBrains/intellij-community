// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    }
  }

  class Options {
    /**
     * [Specification](https://spdx.github.io/spdx-spec/v2.3/document-creation-information/#68-creator-field)
     */
    var creator: String? = null

    /**
     * [Specification](https://spdx.github.io/spdx-spec/v2.3/package-information/#717-copyright-text-field)
     */
    var copyrightText: String? = null

    /**
     * [Specification](https://spdx.github.io/spdx-spec/v2.3/package-information/#715-declared-license-field)
     */
    var license: DistributionLicense? = null

    class DistributionLicense(val name: String, val text: String, val url: String?) {
      internal companion object {
        val JETBRAINS = DistributionLicense(LibraryLicense.JETBRAINS_OWN, LibraryLicense.JETBRAINS_OWN, null)
      }
    }
  }
}