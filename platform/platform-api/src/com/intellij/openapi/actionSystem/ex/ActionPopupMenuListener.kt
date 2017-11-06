package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.actionSystem.ActionPopupMenu

interface ActionPopupMenuListener {
    fun onActionPopupMenuCreated(menu: ActionPopupMenu) {}

    fun onActionPopupMenuReleased(menu: ActionPopupMenu) {}
}