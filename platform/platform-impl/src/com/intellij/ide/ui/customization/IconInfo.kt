// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.IconLoader
import com.intellij.util.text.nullize
import javax.swing.Icon

internal val NONE = IconInfo(null, "<None>", "", null)
internal val SEPARATOR = IconInfo(null, "", "", null)

/**
 * @param actionId id of the action that use this icon.
 * @param iconPath path or URL of the icon.
 * @param text template presentation text of the action or file name.
 */
internal class IconInfo(val icon: Icon?,
                        val text: String,
                        val actionId: String?,
                        val iconPath: String?) {
  val iconReference: String
    get() = actionId ?: iconPath ?: error("Either actionId or iconPath should be set")

  override fun toString(): String {
    return text
  }
}

internal fun getDefaultIcons(): List<IconInfo> {
  val icons = listOf(
    getIconInfo(AllIcons.Toolbar.Unknown, "Default icon"),
    getIconInfo(AllIcons.General.Add, "Add"),
    getIconInfo(AllIcons.General.Remove, "Remove"),
    getIconInfo(AllIcons.Actions.Edit, "Edit"),
    getIconInfo(AllIcons.General.Filter, "Filter"),
    getIconInfo(AllIcons.Actions.Find, "Find"),
    getIconInfo(AllIcons.General.GearPlain, "Gear plain"),
    getIconInfo(AllIcons.Actions.ListFiles, "List files"),
    getIconInfo(AllIcons.ToolbarDecorator.Export, "Export"),
    getIconInfo(AllIcons.ToolbarDecorator.Import, "Import")
  )
  return icons.filterNotNull()
}

private fun getIconInfo(icon: Icon, text: String): IconInfo? {
  val iconUrl = (icon as? IconLoader.CachedImageIcon)?.url
  return iconUrl?.let { IconInfo(icon, text, null, it.toString()) }
}

internal fun getAvailableIcons(): List<IconInfo> {
  val actionManager = ActionManager.getInstance()
  return actionManager.getActionIdList("").mapNotNull { actionId ->
    val action = actionManager.getActionOrStub(actionId) ?: return@mapNotNull null
    val icon = action.templatePresentation.icon ?: return@mapNotNull null
    IconInfo(icon, action.templateText.nullize() ?: actionId, actionId, null)
  }
}