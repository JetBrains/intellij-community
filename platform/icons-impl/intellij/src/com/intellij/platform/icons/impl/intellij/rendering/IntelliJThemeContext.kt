// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.ide.ui.LafManager
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.rendering.ThemeImageFilter
import com.intellij.platform.icons.rendering.ThemeSvgPatcher
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.icons.pathTransformGlobalModCount
import com.intellij.ui.svg.getSelectionColorPatcherProvider
import com.intellij.util.SVGLoader
import com.intellij.util.ui.UIUtil
import java.awt.image.ImageFilter

/**
 * The theme of the IDE. It reads the global theme state of the legacy icons on each call, as `CachedImageIcon` does:
 * - the dark state: `IconLoader.setUseDarkIcons`;
 * - the color palette: [SVGLoader.colorPatcherProvider];
 * - the global filter: `IconLoader.setFilter`;
 * - the selection patcher: `setSelectionColorPatcherProvider`;
 * - the gray filter: [UIUtil.getGrayFilter].
 *
 * The [digest] combines the LaF id, [pathTransformGlobalModCount], the palette digest, the global filter identity and
 * the dark state. So a change of any of them rebuilds the renderers once.
 */
internal class IntelliJThemeContext : ThemeContext {
  @Volatile
  private var lastDigest: DigestState? = null

  @Volatile
  private var lastPalette: IntelliJThemeSvgPatcher? = null

  @Volatile
  private var lastSelectionPatcher: IntelliJThemeSvgPatcher? = null

  @Volatile
  private var lastGlobalFilter: IntelliJThemeImageFilter? = null

  override fun digest(): String {
    val lafId = LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: "none"
    val modCount = pathTransformGlobalModCount.get()
    val transform = pathTransform.get()
    val palette = SVGLoader.colorPatcherProvider
    val paletteDigest = palette?.digest()
    val filter = transform.filter
    val dark = transform.isDark
    // The digest is read on each paint, so build the string only when an input changes.
    val last = lastDigest
    if (last != null && last.matches(lafId, modCount, palette, paletteDigest, filter, dark)) {
      return last.digest
    }
    val paletteText = paletteDigest?.joinToString(separator = ",") ?: "none"
    val filterText = filter?.let { IntelliJThemeImageFilter(it).digest() } ?: "none"
    val digest = "$lafId;$modCount;palette=$paletteText;filter=$filterText;dark=$dark"
    lastDigest = DigestState(lafId, modCount, palette, paletteDigest, filter, dark, digest)
    return digest
  }

  override fun isDark(): Boolean = pathTransform.get().isDark

  override fun colorPalette(): ThemeSvgPatcher? {
    val provider = SVGLoader.colorPatcherProvider ?: return null
    return lastPalette?.takeIf { it.provider === provider } ?: IntelliJThemeSvgPatcher(provider).also { lastPalette = it }
  }

  override fun globalFilter(): ThemeImageFilter? {
    val filter = pathTransform.get().filter ?: return null
    return lastGlobalFilter?.takeIf { it.filter === filter } ?: IntelliJThemeImageFilter(filter).also { lastGlobalFilter = it }
  }

  override fun selectionPatcher(): ThemeSvgPatcher? {
    val provider = getSelectionColorPatcherProvider() ?: return null
    return lastSelectionPatcher?.takeIf { it.provider === provider }
           ?: IntelliJThemeSvgPatcher(provider).also { lastSelectionPatcher = it }
  }

  override fun grayFilter(): ThemeImageFilter = IntelliJThemeImageFilter(UIUtil.getGrayFilter())

  @Deprecated("Use isDark(), colorPalette() and globalFilter()")
  override fun imageModifiers(): ImageModifiers = DefaultImageModifiers(isDark = isDark())

  override fun toString(): String = "IntelliJThemeContext(digest=${digest()})"

  private class DigestState(
    val lafId: String,
    val modCount: Int,
    val palette: SVGLoader.SvgElementColorPatcherProvider?,
    val paletteDigest: LongArray?,
    val filter: ImageFilter?,
    val dark: Boolean,
    val digest: String,
  ) {
    fun matches(
      lafId: String,
      modCount: Int,
      palette: SVGLoader.SvgElementColorPatcherProvider?,
      paletteDigest: LongArray?,
      filter: ImageFilter?,
      dark: Boolean,
    ): Boolean =
      this.lafId == lafId && this.modCount == modCount && this.palette === palette &&
      this.paletteDigest.contentEquals(paletteDigest) && this.filter === filter && this.dark == dark
  }
}

/** The theme of the IDE, for the image loads that do not get a theme from a rendering context. */
internal val intelliJThemeContext: ThemeContext = IntelliJThemeContext()
