// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template

import com.intellij.codeInsight.template.impl.TemplateGroup
import org.jetbrains.annotations.ApiStatus

/**
 * Allows template group ordering customization.
 */
@ApiStatus.Internal
open class TemplateGroupOrderProvider {
  open fun compare(g1: TemplateGroup, g2: TemplateGroup): Int =
    g1.name.compareTo(g2.name, ignoreCase = true)
}
