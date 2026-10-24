// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.rendering.ThemeImageFilter
import com.intellij.platform.icons.rendering.ThemeSvgPatcher
import com.intellij.util.SVGLoader
import org.jetbrains.annotations.ApiStatus
import java.awt.image.ImageFilter

/**
 * A theme SVG patch that the IntelliJ image loaders apply: a legacy SVG color patcher [provider], for example the theme
 * `icons.ColorPalette`. Two instances are equal if they have the same [provider].
 */
@ApiStatus.Internal
class IntelliJThemeSvgPatcher(val provider: SVGLoader.SvgElementColorPatcherProvider) : ThemeSvgPatcher {
  /** The legacy digest of [provider], as the image caches use it. */
  override fun digest(): String = provider.digest().joinToString(separator = ",")

  override fun equals(other: Any?): Boolean = other is IntelliJThemeSvgPatcher && other.provider === provider

  override fun hashCode(): Int = System.identityHashCode(provider)

  override fun toString(): String = "IntelliJThemeSvgPatcher(provider=$provider, digest=${digest()})"
}

/**
 * A theme pixel filter that the IntelliJ image loaders apply: an AWT [filter], for example the color blindness filter.
 * An AWT filter has no digest, so the identity of [filter] identifies it. Two instances are equal if they have the same
 * [filter].
 */
@ApiStatus.Internal
class IntelliJThemeImageFilter(val filter: ImageFilter) : ThemeImageFilter {
  override fun digest(): String = "${filter.javaClass.name}@${Integer.toHexString(System.identityHashCode(filter))}"

  override fun equals(other: Any?): Boolean = other is IntelliJThemeImageFilter && other.filter === filter

  override fun hashCode(): Int = System.identityHashCode(filter)

  override fun toString(): String = "IntelliJThemeImageFilter(filter=$filter)"
}

/** The legacy SVG color patcher of the theme color palette, if the IntelliJ image loaders can apply it. */
@ApiStatus.Internal
fun ThemeContext.intelliJColorPalette(): SVGLoader.SvgElementColorPatcherProvider? =
  (colorPalette() as? IntelliJThemeSvgPatcher)?.provider

/** The AWT filter of the theme global filter, if the IntelliJ image loaders can apply it. */
@ApiStatus.Internal
fun ThemeContext.intelliJGlobalFilter(): ImageFilter? = (globalFilter() as? IntelliJThemeImageFilter)?.filter
