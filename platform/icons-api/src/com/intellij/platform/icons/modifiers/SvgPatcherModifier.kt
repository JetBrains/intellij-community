// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.SvgPatcherDesigner
import com.intellij.platform.icons.patchers.SvgPatcher
import com.intellij.platform.icons.patchers.svgPatcher

fun IconModifier.patchSvg(svgPatcher: SvgPatcher): IconModifier = this then IconManager.modifiers().patchSvg(svgPatcher)

fun IconModifier.patchSvg(svgPatcherBuilder: SvgPatcherDesigner.() -> Unit): IconModifier =
    this.patchSvg(svgPatcher(svgPatcherBuilder))
