// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

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

  private fun removeSkippedDistributionDependencyIds(moduleName: String, dependencyIds: List<String>): List<String> {
    @Suppress("UNCHECKED_CAST")
    return removeSkippedDistributionDependencyIdsMethod.invoke(null, moduleName, dependencyIds) as List<String>
  }

  private companion object {
    private val removeSkippedDistributionDependencyIdsMethod: Method =
      Class.forName("org.jetbrains.intellij.build.impl.RuntimeModuleRepositoryForDistributionKt")
        .getDeclaredMethod("removeSkippedDistributionDependencyIds", String::class.java, List::class.java)
        .apply { isAccessible = true }
  }
}
