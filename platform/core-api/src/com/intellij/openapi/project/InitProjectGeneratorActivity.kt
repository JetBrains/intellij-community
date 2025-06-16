// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Runs an activity before the Create Project dialog is shown.
 */
@Internal
interface InitProjectGeneratorActivity {
  suspend fun run(project: Project?)
}
