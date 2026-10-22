// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Makes test class, test method, and pattern configurations take the runner from the JUnit libraries in the module scope,
 * as package configurations do, instead of from the framework of the test class.
 * Without the JUnit Platform in the scope, the JUnit 4 runner still runs the tests.
 */
@ApiStatus.Internal
interface JUnitRunnerSelectionPolicy {
  fun isPackageRunnerPreferred(project: Project, module: Module?): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<JUnitRunnerSelectionPolicy> = ExtensionPointName("com.intellij.junitRunnerSelectionPolicy")
  }
}
