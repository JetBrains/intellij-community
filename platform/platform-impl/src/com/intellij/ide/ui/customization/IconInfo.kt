// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.IconLoader
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal val NONE = IconInfo(null, IdeBundle.message("default.icons.none.text"), "", null)
internal val SEPARATOR = IconInfo(null, "", "", null)

/**
 * @param actionId id of the action that use this icon.
 * @param iconPath path or URL of the icon.
 * @param text template presentation text of the action or file name.
 */
internal class IconInfo(val icon: Icon?,
                        @Nls val text: String,
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
    getIconInfo(AllIcons.Toolbar.Unknown, IdeBundle.message("default.icons.unknown.text")),
    getIconInfo(AllIcons.General.Add, IdeBundle.message("default.icons.add.text")),
    getIconInfo(AllIcons.General.Remove, IdeBundle.message("default.icons.remove.text")),
    getIconInfo(AllIcons.Actions.Edit, IdeBundle.message("default.icons.edit.text")),
    getIconInfo(AllIcons.General.Filter, IdeBundle.message("default.icons.filter.text")),
    getIconInfo(AllIcons.Actions.Find, IdeBundle.message("default.icons.find.text")),
    getIconInfo(AllIcons.General.GearPlain, IdeBundle.message("default.icons.gear.plain.text")),
    getIconInfo(AllIcons.Actions.ListFiles, IdeBundle.message("default.icons.list.files.text")),
    getIconInfo(AllIcons.ToolbarDecorator.Export, IdeBundle.message("default.icons.export.text")),
    getIconInfo(AllIcons.ToolbarDecorator.Import, IdeBundle.message("default.icons.import.text"))
  )
  return icons.filterNotNull()
}

private fun getIconInfo(icon: Icon, @Nls text: String): IconInfo? {
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