// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafReference
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import javax.swing.ListCellRenderer

/** The unscaled height of a row. It does not depend on the theme, unlike the default `List.rowHeight`. */
private const val ROW_HEIGHT = 24

/** The unscaled room after the widest content of a row, so that the text does not touch the scrollbar. */
private const val ROW_WIDTH_RESERVE = 16

private const val MAX_NAME_LENGTH = 50

/**
 * Creates the renderer of the theme list.
 *
 * The rows have one height and one width, so the popup keeps its size when the theme or the brightness changes.
 * The width is the width of the widest row among [model] themes and [moreItem]. A name longer than
 * [MAX_NAME_LENGTH] characters ends with an ellipsis.
 *
 * A row has a leading icon: a check mark for [activeThemeId], a gear for [moreItem], and an empty icon otherwise.
 * The first theme of a plugin gets the Custom title. [firstCustomThemeId] returns its id.
 */
internal fun createThemeListRenderer(
  list: JBList<LafReference>,
  model: ThemeModel,
  moreItem: LafReference,
  activeThemeId: String,
  firstCustomThemeId: () -> String?,
): ListCellRenderer<LafReference> {
  val allThemes = model.themes(filter = null)
  val restartRequiredIds = allThemes.filter(model::isRestartRequired).mapTo(HashSet()) { it.themeId }
  var fixedRowWidth: Int? = null

  val renderer = listCellRenderer<LafReference> {
    val name = value.name
    val shortName = StringUtil.shortenTextWithEllipsis(name, MAX_NAME_LENGTH, 0, true)
    val themeId = value.themeId
    toolTipText = if (shortName != name) name else null
    icon(
      when {
        themeId.isEmpty() -> AllIcons.General.Settings
        themeId == activeThemeId -> AllIcons.General.GreenCheckmark
        else -> EmptyIcon.ICON_16
      }
    )
    text(shortName) { accessibleName = name }
    if (themeId.isEmpty()) {
      separator { }
    }
    else {
      if (themeId in restartRequiredIds) {
        icon(Badge.beta)
      }
      if (themeId == firstCustomThemeId()) {
        separator { text = IdeBundle.message("combobox.list.custom.section.title") }
      }
    }
    rowHeight = JBUI.scale(ROW_HEIGHT)
    rowWidth = fixedRowWidth
  }

  // Index -1 stands for the collapsed item of a combobox, which has no insets, so the width would be too small.
  fixedRowWidth = (allThemes + moreItem)
    .maxOfOrNull { renderer.getListCellRendererComponent(list, it, 0, false, false).preferredSize.width }
    ?.plus(JBUI.scale(ROW_WIDTH_RESERVE))
  return renderer
}
