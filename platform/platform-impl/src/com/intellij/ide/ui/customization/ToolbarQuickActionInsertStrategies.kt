// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.openapi.keymap.impl.ui.Group
import java.util.ArrayList

class BeforeAction(private val rootID: String, private val destActionID: String): ToolbarQuickActionInsertStrategy {
  override fun addToSchema(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val (path, index) = calcPath(rootID, destActionID, schema, false) ?: return false
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, index) }
      .reversed()
      .forEach { schema.addAction(it) }

    return true
  }
}

class AfterAction(private val rootID: String, private val destActionID: String): ToolbarQuickActionInsertStrategy {
  override fun addToSchema(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val (path, index) = calcPath(rootID, destActionID, schema, false) ?: return false
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, index + 1) }.forEach { schema.addAction(it) }
    return true
  }
}

class GroupStart(private val rootID: String, private val groupID: String = rootID) : ToolbarQuickActionInsertStrategy {
  override fun addToSchema(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val path = calcPath(rootID, groupID, schema, true)?.first ?: return false
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, 0) }
      .reversed()
      .forEach { schema.addAction(it) }
    return true
  }
}

class GroupEnd(private val rootID: String, private val groupID: String = rootID) : ToolbarQuickActionInsertStrategy {
  override fun addToSchema(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    val path = calcPath(rootID, groupID, schema, true)?.first ?: return false
    val index = CustomizationUtil.getGroup(groupID, schema)?.size ?: return false
    actionIds.map { id -> ActionUrl(path, id, ActionUrl.ADDED, index) }.forEach { schema.addAction(it) }
    return true
  }
}

fun groupContainsAction(groupID: String, actionID: String, schema: CustomActionsSchema): Boolean {
  return calcPath(groupID, actionID, schema, false) != null
}

private fun calcPath(parentID: String, destID: String, schema: CustomActionsSchema, inclusive: Boolean): Pair<ArrayList<String>, Int>? {
  val group = CustomizationUtil.getGroup(parentID, schema) ?: return null
  val found = findChild(group, destID, inclusive)
  if (found != null) {
    val list = listOf("root") + found.first
    return Pair(ArrayList(list), found.second)
  }

  return null
}

private fun findChild(group: Group, lookForID: String, inclusive: Boolean): Pair<List<String>, Int>? {
  group.children.forEachIndexed { index, child ->
    if (child is String && child == lookForID) {
      val path = if (inclusive) listOf(group.name, child) else listOf(group.name)
      return Pair(path, index)
    }
    else if (child is Group && child.id == lookForID) {
      val path = if (inclusive) listOf(group.name, child.name) else listOf(group.name)
      return Pair(path, index)
    }
    else if (child is Group) {
      val found = findChild(child, lookForID, inclusive)
      if (found != null) return Pair(listOf(group.name) + (found.first), found.second)
    }
  }

  return null
}