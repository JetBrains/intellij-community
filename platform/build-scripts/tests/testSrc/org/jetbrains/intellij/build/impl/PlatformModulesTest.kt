// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

internal class PlatformModulesTest {
  @Test
  fun `reports all implicit project library violations in stable order`() {
    assertThatIllegalStateException().isThrownBy {
      checkImplicitProjectLibraryViolations(
        mapOf(
          "second-library" to setOf("second-module", "first-module"),
          "first-library" to setOf("only-module"),
        )
      )
    }.withMessage(
      """
        Project libraries used by implicit platform modules must be converted to content modules:
          'first-library' used by 'only-module'
          'second-library' used by 'first-module', 'second-module'
      """.trimIndent()
    )
  }

  @Test
  fun `accepts an empty set of implicit project library violations`() {
    checkImplicitProjectLibraryViolations(emptyMap())
  }
}
