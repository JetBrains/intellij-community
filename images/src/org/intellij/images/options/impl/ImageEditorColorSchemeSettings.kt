// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.options.impl

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.intellij.images.ImagesBundle
import org.intellij.images.fileTypes.impl.ImageFileType

/**
 * @author Konstantin Bulenkov
 */
val BACKGROUND_COLOR_KEY = ColorKey.createColorKey("IMAGES_BACKGROUND", JBColor.background())
val WHITE_CELL_COLOR_KEY = ColorKey.createColorKey("IMAGES_WHITE_CELL_COLOR", Gray.xFF)
val GRID_LINE_COLOR_KEY = ColorKey.createColorKey("IMAGES_GRID_LINE_COLOR", JBColor.DARK_GRAY)
val BLACK_CELL_COLOR_KEY = ColorKey.createColorKey("IMAGES_WHITE_CELL_COLOR", Gray.xC0)

class ImageEditorColorSchemeSettings : ColorSettingsPage, DisplayPrioritySortable {
  override fun getColorDescriptors(): Array<ColorDescriptor> {
    return arrayOf(
      ColorDescriptor(ImagesBundle.message("background.color.descriptor"), BACKGROUND_COLOR_KEY, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(ImagesBundle.message("grid.line.color.descriptor"), GRID_LINE_COLOR_KEY, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(ImagesBundle.message("white.cell.color.descriptor"), WHITE_CELL_COLOR_KEY, ColorDescriptor.Kind.BACKGROUND),
      ColorDescriptor(ImagesBundle.message("black.cell.color.descriptor"), BLACK_CELL_COLOR_KEY, ColorDescriptor.Kind.BACKGROUND))
  }

  override fun getPriority() = DisplayPriority.OTHER_SETTINGS
  override fun getDisplayName() = ImagesBundle.message("settings.page.name")
  override fun getIcon() = ImageFileType.INSTANCE.icon
  override fun getDemoText() = " "
  override fun getHighlighter() = PlainSyntaxHighlighter()
  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
  override fun getAttributeDescriptors() = emptyArray<AttributesDescriptor>()
}