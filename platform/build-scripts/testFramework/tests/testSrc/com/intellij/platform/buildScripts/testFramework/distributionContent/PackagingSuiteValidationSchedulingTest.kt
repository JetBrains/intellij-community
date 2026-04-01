// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PackagingSuiteValidationSchedulingTest {
  @Test
  fun `source-only validation skips the compile gate`() = runBlocking {
    val compileProductionModulesDeferred = CompletableDeferred<Unit>()
    val sourceOnlyValidation = PackagingSuiteValidationSpec(
      name = "source-only",
      problemMessage = "source-only",
      requiresCompilation = false,
      validator = { emptyList() },
    )
    val compileDependentValidation = sourceOnlyValidation.copy(name = "compile-dependent", requiresCompilation = true)

    val sourceOnlyAwaiter = async {
      awaitPackagingSuiteValidationCompilationIfRequired(sourceOnlyValidation, compileProductionModulesDeferred)
    }
    val compileDependentAwaiter = async {
      awaitPackagingSuiteValidationCompilationIfRequired(compileDependentValidation, compileProductionModulesDeferred)
    }

    yield()

    assertThat(sourceOnlyAwaiter.isCompleted).isTrue()
    assertThat(compileDependentAwaiter.isCompleted).isFalse()

    compileProductionModulesDeferred.complete(Unit)
    compileDependentAwaiter.await()
  }
}
