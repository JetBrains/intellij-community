// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.platform.experiment.ab.impl.ABExperimentOption
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal fun isFuzzyFileSearchEnabled(): Boolean {
  return `is`("search.everywhere.fuzzy.file.search.enabled", false) || ABExperimentOption.FUZZY_FILE_SEARCH.isEnabled()
}