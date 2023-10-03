// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting

import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.ColorizeProxyIcon
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.NonNls
import java.awt.Color
import javax.swing.Icon

open class HighlightDisplayLevel(val severity: HighlightSeverity) {
  constructor(severity: HighlightSeverity, icon: Icon) : this(severity, icon, icon)

  constructor(severity: HighlightSeverity, icon: Pair<Icon, Icon>) : this(severity, icon.first, icon.second)

  private constructor(severity: HighlightSeverity, icon: Icon, outlineIcon: Icon) : this(severity) {
    this.icon = icon
    this.outlineIcon = outlineIcon
    LEVEL_MAP[this.severity] = this
  }

  var icon: Icon = EmptyIcon.ICON_16
    private set
  var outlineIcon: Icon = EmptyIcon.ICON_16
    private set

  companion object {
    private val LEVEL_MAP: MutableMap<HighlightSeverity, HighlightDisplayLevel> = HashMap()

    @JvmField
    val GENERIC_SERVER_ERROR_OR_WARNING: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                                                                                       createIconPair(
                                                                                         CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING,
                                                                                         AllIcons.General.InspectionsWarning,
                                                                                         AllIcons.General.InspectionsWarningEmpty))

    @JvmField
    val ERROR: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.ERROR,
                                                             createIconPair(CodeInsightColors.ERRORS_ATTRIBUTES,
                                                                            AllIcons.General.InspectionsError,
                                                                            AllIcons.General.InspectionsErrorEmpty))

    @JvmField
    val WARNING: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.WARNING,
                                                               createIconPair(CodeInsightColors.WARNINGS_ATTRIBUTES,
                                                                              AllIcons.General.InspectionsWarning,
                                                                              AllIcons.General.InspectionsWarningEmpty))

    private val DO_NOT_SHOW_KEY = TextAttributesKey.createTextAttributesKey("DO_NOT_SHOW")
    @JvmField
    val DO_NOT_SHOW: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.INFORMATION, EmptyIcon.ICON_0)

    @JvmField
    val CONSIDERATION_ATTRIBUTES: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.TEXT_ATTRIBUTES, EmptyIcon.ICON_0)


    @JvmField
    @Deprecated("use {@link #WEAK_WARNING} instead")
    val INFO: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.INFO, createIconByKey(DO_NOT_SHOW_KEY))

    @JvmField
    val WEAK_WARNING: HighlightDisplayLevel = HighlightDisplayLevel(HighlightSeverity.WEAK_WARNING,
                                                                    createIconPair(CodeInsightColors.WEAK_WARNING_ATTRIBUTES,
                                                                                   AllIcons.General.InspectionsWarning,
                                                                                   AllIcons.General.InspectionsWarningEmpty))

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
    fun find(name: String): HighlightDisplayLevel? {
      if ("NON_SWITCHABLE_ERROR" == name) return NON_SWITCHABLE_ERROR
      if ("NON_SWITCHABLE_WARNING" == name) return NON_SWITCHABLE_WARNING
      for ((severity, displayLevel) in LEVEL_MAP) {
        if (Comparing.strEqual(severity.name, name)) {
          return displayLevel
        }
      }
      return null
    }

    @JvmStatic
    fun find(severity: HighlightSeverity): HighlightDisplayLevel? {
      return LEVEL_MAP[severity]
    }

    @JvmStatic
    fun registerSeverity(severity: HighlightSeverity, key: TextAttributesKey, icon: Icon?) {
      val iconPair = if (icon == null) createIconByKey(key) else Pair(icon, icon)
      val level = LEVEL_MAP[severity]
      if (level == null) {
        HighlightDisplayLevel(severity, iconPair.first, iconPair.second)
      }
      else {
        level.icon = iconPair.first
        level.outlineIcon = iconPair.second
      }
    }

    val emptyIconDim: Int
      get() = scale(14)

    @JvmStatic
    fun createIconByMask(renderColor: Color): Icon {
      return MyColorIcon(emptyIconDim, renderColor)
    }
  }

  override fun toString(): @NonNls String {
    return severity.toString()
  }

  val name: @NonNls String
    get() = severity.name

  open val isNonSwitchable: Boolean
    get() = false

  private class MyColorIcon(size: Int, color: Color) : ColorIcon(size, color), ColoredIcon {
    override val color: Color
      get() = iconColor
  }

  interface ColoredIcon {
    val color: Color
  }

  private class ColorizedIcon(private val myKey: TextAttributesKey, baseIcon: Icon) : ColorizeProxyIcon(baseIcon), ColoredIcon {
    override fun getColor(): Color {
      return ObjectUtils.notNull(getColorFromAttributes(myKey), JBColor.GRAY)
    }
  }
}

private fun getColorFromAttributes(key: TextAttributesKey): Color? {
  val manager = EditorColorsManager.getInstance()
  if (manager != null) {
    val attributes = manager.getGlobalScheme().getAttributes(key)
    val stripe = attributes?.errorStripeColor
    if (stripe != null) return stripe
    if (attributes != null) {
      val effectColor = attributes.effectColor
      if (effectColor != null) {
        return effectColor
      }
      val foregroundColor = attributes.foregroundColor
      if (foregroundColor != null) {
        return foregroundColor
      }
      return attributes.backgroundColor
    }
    return null
  }
  var defaultAttributes = key.getDefaultAttributes()
  if (defaultAttributes == null) {
    defaultAttributes = TextAttributes.ERASE_MARKER
  }
  return defaultAttributes!!.errorStripeColor
}

private fun createIconByKey(key: TextAttributesKey): Pair<Icon, Icon> {
  return if (StringUtil.containsIgnoreCase(key.externalName, "error")) {
    createIconPair(key, AllIcons.General.InspectionsError, AllIcons.General.InspectionsErrorEmpty)
  }
  else {
    createIconPair(key, AllIcons.General.InspectionsWarning, AllIcons.General.InspectionsWarningEmpty)
  }
}

private fun createIconPair(key: TextAttributesKey, first: Icon, second: Icon): Pair<Icon, Icon> {
  return Pair(HighlightDisplayLevel.ColorizedIcon(key, first), HighlightDisplayLevel.ColorizedIcon(key, second))
}