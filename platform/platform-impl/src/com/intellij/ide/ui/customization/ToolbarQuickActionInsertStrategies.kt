// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.ui.customization

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BeforeAction(private val rootID: String, private val destActionID: String): ToolbarQuickActionInsertStrategy {
  override fun addActions(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val group = getGroup(rootID, schema) ?: return false
    val (path, index) = calcPath(group, destActionID, false) ?: return false
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, index) }
      .reversed()
      .forEach { schema.addAction(it) }

    return true
  }

  override fun checkExists(actionId: String, schema: CustomActionsSchema) = groupContainsAction(rootID, actionId, schema)
}

@ApiStatus.Internal
class AfterAction(private val rootID: String, private val destActionID: String): ToolbarQuickActionInsertStrategy {
  override fun addActions(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val group = getGroup(rootID, schema) ?: return false
    val (path, index) = calcPath(group, destActionID, false) ?: return false
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, index + 1) }.forEach { schema.addAction(it) }
    return true
  }

  override fun checkExists(actionId: String, schema: CustomActionsSchema) = groupContainsAction(rootID, actionId, schema)
}

class GroupStart(private val rootID: String, private val groupID: String = rootID) : ToolbarQuickActionInsertStrategy {
  override fun addActions(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val group = getGroup(rootID, schema) ?: return false
    val path = if (groupID == rootID) {
      ArrayList(listOf("root", group.name))
    }
    else {
      ActionManager.getInstance().getAction(groupID)
        ?.let { StringUtil.nullize(ActionsTreeUtil.getName(it)) }
        ?.let { calcPath(group, it, true) }
        ?.first ?: return false
    }
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, 0) }
      .reversed()
      .forEach { schema.addAction(it) }
    return true
  }

  override fun checkExists(actionId: String, schema: CustomActionsSchema) = groupContainsAction(rootID, actionId, schema)
}

class GroupEnd(private val rootID: String, private val groupID: String = rootID) : ToolbarQuickActionInsertStrategy {
  override fun addActions(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val group = getGroup(rootID, schema) ?: return false
    var path: ArrayList<String>
    var index: Int
    if (groupID == rootID) {
      path = ArrayList(listOf("root", group.name))
      index = group.size
    }
    else {
      path = ActionManager.getInstance().getAction(groupID)
               ?.let { StringUtil.nullize(ActionsTreeUtil.getName(it)) }
               ?.let { calcPath(group, it, true) }
               ?.first ?: return false
      index = getSubGroup(group, path)?.size ?: return false
    }
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, index) }
      .reversed()
      .forEach { schema.addAction(it) }
    return true
  }

  override fun checkExists(actionId: String, schema: CustomActionsSchema) = groupContainsAction(rootID, actionId, schema)
}

fun groupContainsAction(groupID: String, actionID: String, schema: CustomActionsSchema): Boolean {
  val group = getGroup(groupID, schema) ?: return false
  return calcPath(group, actionID, false) != null
}

private fun getGroup(groupID: String, schema: CustomActionsSchema): Group? {
  val group = (schema.getCorrectedAction(groupID) as? ActionGroup) ?: return null
  @NlsSafe val name = schema.getDisplayName(groupID)
  return ActionsTreeUtil.createGroup(group, name, null, false, { _: AnAction? -> true }, false)
}

private fun getSubGroup(group: Group, path: List<String>): Group? {
  var currentGroup = group
  val searchPath = path.subList(2, path.size)
  for (str in searchPath) {
    if (str == "root") continue
    currentGroup = (currentGroup.children.find { (it as? Group)?.name == str } as? Group) ?: return null
  }
  return currentGroup
}

private fun calcPath(group: Group, searchStr: String, inclusive: Boolean): Pair<ArrayList<String>, Int>? {
  val found = findChild(group, searchStr, inclusive)
  if (found != null) {
    val list = listOf("root") + found.first
    return Pair(ArrayList(list), found.second)
  }

  return null
}

private fun findChild(group: Group, searchStr: String, inclusive: Boolean): Pair<List<String>, Int>? {
  group.children.forEachIndexed { index, child ->
    if (child is String && child == searchStr) {
      val path = if (inclusive) listOf(group.name, child) else listOf(group.name)
      return Pair(path, index)
    }
    else if (child is Group && (child.name == searchStr || child.id == searchStr)) {
      val path = if (inclusive) listOf(group.name, child.name) else listOf(group.name)
      return Pair(path, index)
    }
    else if (child is Group) {
      val found = findChild(child, searchStr, inclusive)
      if (found != null) return Pair(listOf(group.name) + (found.first), found.second)
    }
  }

  return null
}