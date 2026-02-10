// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.SvgPatcherDesigner
import org.jetbrains.icons.patchers.SvgPatcher

@Serializable
@ExperimentalIconsApi
class SvgPatcherModifier(
  val svgPatcher: SvgPatcher
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SvgPatcherModifier

    return svgPatcher == other.svgPatcher
  }

  override fun hashCode(): Int {
    return svgPatcher.hashCode()
  }

  override fun toString(): String {
    return "SvgPatcherModifier(svgPatcher=$svgPatcher)"
  }

}

@ExperimentalIconsApi
fun IconModifier.patchSvg(svgPatcher: SvgPatcher): IconModifier {
  return this then SvgPatcherModifier(svgPatcher)
}

@ExperimentalIconsApi
fun IconModifier.patchSvg(svgPatcherBuilder: SvgPatcherDesigner.() -> Unit): IconModifier {
  return this.patchSvg(svgPatcher(svgPatcherBuilder))
}

@ExperimentalIconsApi
fun svgPatcher(svgPatcherBuilder: SvgPatcherDesigner.() -> Unit): SvgPatcher = SvgPatcherDesigner().apply(svgPatcherBuilder).build()