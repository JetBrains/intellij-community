// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import org.jetbrains.annotations.ApiStatus

/**
 * The theme inputs of icon rendering.
 *
 * The renderers read each input when they need it, not when the rendering context is created. So a long-lived renderer
 * follows a theme change. An icon renderer rebuilds its layer renderers when [digest] changes. So [digest] must change
 * when any input changes.
 *
 * A theme input is `null` if the theme does not supply it.
 */
interface ThemeContext {
  /**
   * Identifies the current state of all theme inputs. It changes when an input changes, so the renderers are rebuilt
   * once for each change. It is read on each paint, so it must be cheap.
   */
  fun digest(): String

  /**
   * The dark state of the theme: `true` for the dark artwork, `false` for the light artwork. An image layer uses it if
   * neither the layer nor the rendering context sets an explicit dark state. If it is `null`, the image layers keep the
   * dark state of the rendering context.
   */
  fun isDark(): Boolean? = null

  /** The theme color palette. The image loaders apply it to each SVG image, under the SVG patches of the icon. */
  fun colorPalette(): ThemeSvgPatcher? = null

  /** The filter that the image loaders apply to each image, before the color filter of the icon. */
  fun globalFilter(): ThemeImageFilter? = null

  /** The SVG patch for images on a selected background. The renderers do not apply it. */
  fun selectionPatcher(): ThemeSvgPatcher? = null

  /** The filter for disabled images. The renderers do not apply it. */
  fun grayFilter(): ThemeImageFilter? = null

  /**
   * Temporary method to get image modifiers from theme. The renderers still read it for a theme that overrides it:
   * its dark state is used if [isDark] is `null`, and its modifiers are the defaults of a rendering context without
   * own modifiers.
   */
  @Deprecated("Use isDark(), colorPalette() and globalFilter()")
  @ApiStatus.Internal
  fun imageModifiers(): ImageModifiers? = null

  companion object {
    val None: ThemeContext = object : ThemeContext {
      override fun digest(): String = "NONE"
    }
  }
}

/**
 * An SVG color patch that a theme supplies, for example the theme color palette.
 *
 * Each renderer backend defines the implementations that it can apply. A backend does not apply an implementation that
 * it does not know.
 */
interface ThemeSvgPatcher {
  /** Identifies the patch. Two patchers with the same digest patch the same colors. */
  fun digest(): String
}

/**
 * A pixel filter that a theme supplies, for example the color blindness filter.
 *
 * Each renderer backend defines the implementations that it can apply. A backend does not apply an implementation that
 * it does not know.
 */
interface ThemeImageFilter {
  /** Identifies the filter. Two filters with the same digest give the same pixels. */
  fun digest(): String
}
