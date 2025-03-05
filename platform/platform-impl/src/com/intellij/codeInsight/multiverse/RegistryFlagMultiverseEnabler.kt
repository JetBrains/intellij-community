// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

internal class RegistryFlagMultiverseEnabler : MultiverseEnabler {
  override fun enableMultiverse(project: Project): Boolean =
    Registry.`is`("intellij.platform.shared.source.support")
}