// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.util.IconLoader
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import java.io.IOException
import javax.swing.Icon

internal val NONE = ActionIconInfo(null, IdeBundle.message("default.icons.none.text"), "", null)
internal val SEPARATOR = ActionIconInfo(null, "", "", null)

/**
 * @param actionId id of the action that use this icon.
 * @param iconPath path or URL of the icon.
 * @param text template presentation text of the action or file name.
 */
internal class ActionIconInfo(val icon: Icon?,
                              @Nls val text: String,
                              val actionId: String?,
                              val iconPath: String?) {
  val iconReference: String
    get() = iconPath ?: actionId ?: error("Either actionId or iconPath should be set")

  override fun toString(): String {
    return text
  }
}

internal fun getDefaultIcons(): List<ActionIconInfo> {
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

private fun getIconInfo(icon: Icon, @Nls text: String): ActionIconInfo? {
  val iconUrl = (icon as? IconLoader.CachedImageIcon)?.url
  return iconUrl?.let { ActionIconInfo(icon, text, null, it.toString()) }
}

internal fun getAvailableIcons(): List<ActionIconInfo> {
  val actionManager = ActionManager.getInstance()
  return actionManager.getActionIdList("").mapNotNull { actionId ->
    val action = actionManager.getActionOrStub(actionId) ?: return@mapNotNull null
    val icon = if (action is ActionStubBase) {
      val path = action.iconPath ?: return@mapNotNull null
      IconLoader.findIcon(path, action.plugin.classLoader)
    }
    else {
      val presentation = action.templatePresentation
      presentation.getClientProperty(CustomActionsSchema.PROP_ORIGINAL_ICON) ?: presentation.icon
    }
    icon?.let { ActionIconInfo(it, action.templateText.nullize() ?: actionId, actionId, null) }
  }
}

internal fun getCustomIcons(schema: CustomActionsSchema): List<ActionIconInfo> {
  val actionManager = ActionManager.getInstance()
  return schema.iconCustomizations.mapNotNull { (actionId, iconReference) ->
    if (actionId == null || iconReference == null) return@mapNotNull null
    val action = actionManager.getAction(iconReference)
    if (action == null) {
      val icon = try {
        CustomActionsSchema.loadCustomIcon(iconReference)
      }
      catch (ex: IOException) {
        null
      }
      if (icon != null) {
        ActionIconInfo(icon, iconReference.substringAfterLast("/"), actionId, iconReference)
      }
      else null
    }
    else null
  }
}