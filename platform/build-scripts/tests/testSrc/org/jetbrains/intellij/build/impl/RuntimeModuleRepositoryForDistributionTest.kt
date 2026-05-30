// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.moduleRepository.removeSkippedDistributionDependencyIds
import org.junit.jupiter.api.Test

class RuntimeModuleRepositoryForDistributionTest {

  @Test
  fun `keeps unrelated descriptors unchanged`() {
    val dependencyIds = listOf("intellij.libraries.assertj.core")

    val actual = removeSkippedDistributionDependencyIds(
      moduleName = "intellij.platform.ide",
      dependencyIds = dependencyIds,
    )

    assertThat(actual).isSameAs(dependencyIds)
  }
}
