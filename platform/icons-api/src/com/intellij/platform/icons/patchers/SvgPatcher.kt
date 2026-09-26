// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.patchers

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.SvgPatcherDesigner

interface SvgPatcher {
    fun combineWith(other: SvgPatcher?): SvgPatcher?
}

fun svgPatcher(designer: SvgPatcherDesigner.() -> Unit): SvgPatcher = IconManager.getInstance().svgPatcher(designer)
