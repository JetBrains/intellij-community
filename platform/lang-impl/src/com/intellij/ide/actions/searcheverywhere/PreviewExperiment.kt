// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PreviewExperiment {
  @JvmStatic
  val isExperimentEnabled: Boolean
    get() = Registry.`is`("search.everywhere.preview")
}