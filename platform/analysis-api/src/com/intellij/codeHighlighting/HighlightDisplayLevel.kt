// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.codeHighlighting

import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

open class HighlightDisplayLevel(val severity: HighlightSeverity) {
  constructor(severity: HighlightSeverity, icon: Icon) : this(severity = severity, icon = icon, outlineIcon = icon)

  private constructor(severity: HighlightSeverity, icon: Icon, outlineIcon: Icon) : this(severity) {
    this.icon = icon
    this.outlineIcon = outlineIcon
    @Suppress("LeakingThis")
    LEVEL_MAP.put(this.severity, this)
  }

  var icon: Icon = EmptyIcon.ICON_16
    private set
  var outlineIcon: Icon = EmptyIcon.ICON_16
    private set

  companion object {
    private val LEVEL_MAP = HashMap<HighlightSeverity, HighlightDisplayLevel>()

    private fun createHighlightDisplayLevel(severity: HighlightSeverity,
                                            key: TextAttributesKey,
                                            icon: Icon,
                                            outlineIcon: Icon): HighlightDisplayLevel {
      return HighlightDisplayLevel(severity = severity,
                                   icon = HighlightDisplayLevelColorizedIcon(key = key, baseIcon = icon),
                                   outlineIcon = HighlightDisplayLevelColorizedIcon(key = key, baseIcon = outlineIcon))
    }

    @JvmField
    val GENERIC_SERVER_ERROR_OR_WARNING: HighlightDisplayLevel = createHighlightDisplayLevel(
      severity = HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
      key = CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING,
      icon = AllIcons.General.InspectionsWarning,
      outlineIcon = AllIcons.General.InspectionsWarningEmpty,
    )

    @JvmField
    val ERROR: HighlightDisplayLevel = createHighlightDisplayLevel(
      severity = HighlightSeverity.ERROR,
      key = CodeInsightColors.ERRORS_ATTRIBUTES,
      icon = AllIcons.General.InspectionsError,
      outlineIcon = AllIcons.General.InspectionsErrorEmpty,
    )

    @JvmField
    val WARNING: HighlightDisplayLevel = createHighlightDisplayLevel(
      severity = HighlightSeverity.WARNING,
      key = CodeInsightColors.WARNINGS_ATTRIBUTES,
      icon = AllIcons.General.InspectionsWarning,
      outlineIcon = AllIcons.General.InspectionsWarningEmpty,
    )

    private val DO_NOT_SHOW_KEY = TextAttributesKey.createTextAttributesKey("DO_NOT_SHOW")

    @JvmField
    val DO_NOT_SHOW: HighlightDisplayLevel = HighlightDisplayLevel(severity = HighlightSeverity.INFORMATION,
                                                                   icon = EmptyIcon.ICON_0)

    @JvmField
    val CONSIDERATION_ATTRIBUTES: HighlightDisplayLevel = HighlightDisplayLevel(severity = HighlightSeverity.TEXT_ATTRIBUTES,
                                                                                icon = EmptyIcon.ICON_0)


    @Suppress("DEPRECATION")
    @JvmField
    @Deprecated("use {@link #WEAK_WARNING} instead")
    val INFO: HighlightDisplayLevel = createHighlightDisplayLevel(severity = HighlightSeverity.INFO,
                                                                  key = DO_NOT_SHOW_KEY, icon = AllIcons.General.InspectionsWarning,
                                                                  outlineIcon = AllIcons.General.InspectionsWarningEmpty)

    @JvmField
    val WEAK_WARNING: HighlightDisplayLevel = createHighlightDisplayLevel(
      severity = HighlightSeverity.WEAK_WARNING,
      key = CodeInsightColors.WEAK_WARNING_ATTRIBUTES,
      icon = AllIcons.General.InspectionsWarning,
      outlineIcon = AllIcons.General.InspectionsWarningEmpty,
    )

    @JvmField
    val NON_SWITCHABLE_ERROR: HighlightDisplayLevel = object : HighlightDisplayLevel(HighlightSeverity.ERROR) {
      override val isNonSwitchable: Boolean
        get() = true
    }

    @JvmField
    val NON_SWITCHABLE_WARNING: HighlightDisplayLevel = object : HighlightDisplayLevel(HighlightSeverity.WARNING) {
      override val isNonSwitchable: Boolean
        get() = true
    }

    @JvmStatic
    fun find(name: String?): HighlightDisplayLevel? {
      return when (name) {
        "NON_SWITCHABLE_ERROR" -> NON_SWITCHABLE_ERROR
        "NON_SWITCHABLE_WARNING" -> NON_SWITCHABLE_WARNING
        else -> {
          for ((severity, displayLevel) in LEVEL_MAP) {
            if (severity.name == name) {
              return displayLevel
            }
          }
          null
        }
      }
    }

    @JvmStatic
    fun find(severity: HighlightSeverity): HighlightDisplayLevel? = LEVEL_MAP.get(severity)

    @JvmStatic
    fun registerSeverity(severity: HighlightSeverity, key: TextAttributesKey, icon: Icon?) {
      val effectiveIcon: Icon
      val outlineIcon: Icon

      if (icon == null) {
        if (key.externalName.contains("error", ignoreCase = true)) {
          effectiveIcon = HighlightDisplayLevelColorizedIcon(key = key, baseIcon = AllIcons.General.InspectionsError)
          outlineIcon = HighlightDisplayLevelColorizedIcon(key = key, baseIcon = AllIcons.General.InspectionsErrorEmpty)
        }
        else {
          effectiveIcon = HighlightDisplayLevelColorizedIcon(key = key, baseIcon = AllIcons.General.InspectionsWarning)
          outlineIcon = HighlightDisplayLevelColorizedIcon(key = key, baseIcon = AllIcons.General.InspectionsWarningEmpty)
        }
      }
      else {
        effectiveIcon = icon
        outlineIcon = icon
      }

      val level = LEVEL_MAP.get(severity)
      if (level == null) {
        HighlightDisplayLevel(severity = severity, icon = effectiveIcon, outlineIcon = outlineIcon)
      }
      else {
        level.icon = effectiveIcon
        level.outlineIcon = outlineIcon
      }
    }

    @JvmStatic
    val emptyIconDim: Int
      get() = JBUIScale.scale(14)

    @JvmStatic
    fun createIconByMask(renderColor: Color): Icon {
      return HighlightDisplayLevelColorIcon(size = emptyIconDim, color = renderColor)
    }
  }

  override fun toString(): @NonNls String = severity.toString()

  val name: @NonNls String
    get() = severity.name

  open val isNonSwitchable: Boolean
    get() = false
}

sealed interface HighlightDisplayLevelColoredIcon {
  fun getColor(): Color
}

private class HighlightDisplayLevelColorIcon(size: Int, color: Color) : ColorIcon(size, color), HighlightDisplayLevelColoredIcon {
  override fun getColor(): Color = iconColor
}

private class HighlightDisplayLevelColorizedIcon(private val key: TextAttributesKey,
                                                 baseIcon: Icon) : Icon, HighlightDisplayLevelColoredIcon {
  private val baseIcon = IconManager.getInstance().colorizedIcon(baseIcon = baseIcon, colorProvider = ::getColor)

  private var lastEditorColorManagerModCounter = -1L
  private var lastColor: Color? = null

  override fun getColor(): Color = getColorFromAttributes(key) ?: JBColor.GRAY

  private fun getColorFromAttributes(key: TextAttributesKey): Color? {
    val editorColorManager = EditorColorsManager.getInstance()
                             ?: return (key.getDefaultAttributes() ?: TextAttributes.ERASE_MARKER).errorStripeColor
    lastColor?.takeIf { editorColorManager.schemeModificationCounter == lastEditorColorManagerModCounter}?.let {
      return it
    }

    val attributes = editorColorManager.getGlobalScheme().getAttributes(key)
    val stripe = attributes?.errorStripeColor
    val result = when {
      stripe != null -> stripe
      attributes == null -> null
      else -> attributes.effectColor ?: attributes.foregroundColor ?: attributes.backgroundColor
    }

    lastEditorColorManagerModCounter = editorColorManager.schemeModificationCounter
    lastColor = result
    return result
  }

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    baseIcon.paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int = baseIcon.iconWidth

  override fun getIconHeight(): Int = baseIcon.iconHeight
}