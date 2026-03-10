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
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

open class HighlightDisplayLevel(severity: HighlightSeverity) {
  @Internal
  data class SeverityDescriptor(
    @JvmField val severity: HighlightSeverity,
    @JvmField val attributesKey: TextAttributesKey,
    @JvmField val icon: Icon?,
  )

  constructor(severity: HighlightSeverity, icon: Icon) : this(severity = severity, icon = icon, outlineIcon = icon)

  private constructor(severity: HighlightSeverity, icon: Icon, outlineIcon: Icon) : this(severity) {
    this.icon = icon
    this.outlineIcon = outlineIcon
    @Suppress("LeakingThis")
    registerLevel(this)
  }

  var severity: HighlightSeverity = severity
    private set

  var icon: Icon = EmptyIcon.ICON_16
    private set
  var outlineIcon: Icon = EmptyIcon.ICON_16
    private set

  companion object {
    private data class LevelPresentation(
      @JvmField val key: TextAttributesKey,
      @JvmField val icon: Icon?,
      @JvmField val outlineIcon: Icon?,
    )

    private data class LevelStateBackup(
      @JvmField val severity: HighlightSeverity,
      @JvmField val icon: Icon,
      @JvmField val outlineIcon: Icon,
    )

    private val LEVEL_MAP = ConcurrentHashMap<String, HighlightDisplayLevel>()
    private val LEVEL_LOCK = Any()
    private val providedLevelNames = HashSet<String>()
    private val shadowedLevelBackups = HashMap<String, LevelStateBackup>()

    private fun registerLevel(level: HighlightDisplayLevel) {
      LEVEL_MAP[level.severity.name] = level
    }

    private fun createPresentation(
      key: TextAttributesKey,
      icon: Icon?,
      outlineIcon: Icon? = icon,
    ): LevelPresentation {
      return LevelPresentation(key = key, icon = icon, outlineIcon = outlineIcon)
    }

    private fun updateLevel(level: HighlightDisplayLevel, severity: HighlightSeverity, presentation: LevelPresentation) {
      val (icon, outlineIcon) = createIcons(presentation)
      level.severity = severity
      level.icon = icon
      level.outlineIcon = outlineIcon
      registerLevel(level)
    }

    private fun restoreLevel(levelName: String) {
      val level = LEVEL_MAP[levelName] ?: return
      val backup = shadowedLevelBackups.remove(levelName)
      if (backup == null) {
        LEVEL_MAP.remove(levelName)
      }
      else {
        level.severity = backup.severity
        level.icon = backup.icon
        level.outlineIcon = backup.outlineIcon
        registerLevel(level)
      }
    }

    private fun createIcons(presentation: LevelPresentation): Pair<Icon, Icon> {
      presentation.icon?.let { icon ->
        return icon to (presentation.outlineIcon ?: icon)
      }

      return if (presentation.key.externalName.contains("error", ignoreCase = true)) {
        HighlightDisplayLevelColorizedIcon(key = presentation.key, baseIcon = AllIcons.General.InspectionsError) to
        HighlightDisplayLevelColorizedIcon(key = presentation.key, baseIcon = AllIcons.General.InspectionsErrorEmpty)
      }
      else {
        HighlightDisplayLevelColorizedIcon(key = presentation.key, baseIcon = AllIcons.General.InspectionsWarning) to
        HighlightDisplayLevelColorizedIcon(key = presentation.key, baseIcon = AllIcons.General.InspectionsWarningEmpty)
      }
    }

    private fun presentationFrom(descriptor: SeverityDescriptor): LevelPresentation {
      return createPresentation(key = descriptor.attributesKey, icon = descriptor.icon)
    }

    private fun createHighlightDisplayLevel(
      severity: HighlightSeverity,
      key: TextAttributesKey,
      icon: Icon,
      outlineIcon: Icon,
    ): HighlightDisplayLevel {
      val presentation = createPresentation(key = key, icon = icon, outlineIcon = outlineIcon)
      val (effectiveIcon, effectiveOutlineIcon) = createIcons(presentation)
      return HighlightDisplayLevel(
        severity = severity,
        icon = effectiveIcon,
        outlineIcon = effectiveOutlineIcon,
      )
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
        else -> name?.let { LEVEL_MAP[it] }
      }
    }

    @JvmStatic
    fun find(severity: HighlightSeverity): HighlightDisplayLevel? = LEVEL_MAP[severity.name]

    @JvmStatic
    fun registerSeverity(severity: HighlightSeverity, key: TextAttributesKey, icon: Icon?) {
      val presentation = createPresentation(key = key, icon = icon)
      synchronized(LEVEL_LOCK) {
        val level = LEVEL_MAP[severity.name]
        if (level == null) {
          val (effectiveIcon, outlineIcon) = createIcons(presentation)
          HighlightDisplayLevel(severity = severity, icon = effectiveIcon, outlineIcon = outlineIcon)
        }
        else {
          updateLevel(level, severity, presentation)
        }
      }
    }

    @JvmStatic
    fun syncProvidedSeverities(severities: Map<String, SeverityDescriptor>) {
      synchronized(LEVEL_LOCK) {
        val remainingProvided = LinkedHashMap<String, HighlightDisplayLevel>()
        for (providedLevelName in providedLevelNames) {
          LEVEL_MAP[providedLevelName]?.let { remainingProvided[providedLevelName] = it }
        }

        for ((levelName, descriptor) in severities) {
          val level = LEVEL_MAP[levelName]
          if (level != null && levelName !in providedLevelNames) {
            shadowedLevelBackups.putIfAbsent(levelName, LevelStateBackup(level.severity, level.icon, level.outlineIcon))
          }

          val presentation = presentationFrom(descriptor)
          val severity = descriptor.severity
          if (level == null) {
            val (icon, outlineIcon) = createIcons(presentation)
            HighlightDisplayLevel(severity = severity, icon = icon, outlineIcon = outlineIcon)
          }
          else {
            updateLevel(level, severity, presentation)
          }

          providedLevelNames.add(levelName)
          remainingProvided.remove(levelName)
        }

        for (removedLevelName in remainingProvided.keys) {
          providedLevelNames.remove(removedLevelName)
          restoreLevel(removedLevelName)
        }
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

sealed interface HighlightDisplayLevelColoredIcon : Icon {
  fun getColor(): Color
  fun getIcon(): Icon
}

private class HighlightDisplayLevelColorIcon(size: Int, color: Color) : ColorIcon(size, color), HighlightDisplayLevelColoredIcon {
  override fun getColor(): Color = iconColor
  override fun getIcon(): Icon = this
}

private class HighlightDisplayLevelColorizedIcon(private val key: TextAttributesKey,
                                                 baseIcon: Icon) : Icon, HighlightDisplayLevelColoredIcon {
  private val baseIcon = IconManager.getInstance().colorizedIcon(baseIcon = baseIcon, colorProvider = ::getColor)

  private var lastEditorColorManagerModCounter = -1L
  private var lastColor: Color? = null

  override fun getColor(): Color = getColorFromAttributes(key) ?: JBColor.GRAY
  override fun getIcon(): Icon  = baseIcon

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
