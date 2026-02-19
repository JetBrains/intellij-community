// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

/**
 * For actions and groups with no remote id, i.e. when `ActionProvider.getId(action) == null`,
 * provides id for backend and frontend merging.
 *
 * This id cannot be used to locate an action on backend, for example, to call it, but
 * it helps to avoid dynamic action and group duplication in menus and toolbars.
 */
interface ActionWithMergeId {
  fun getMergeId(): String = javaClass.name
}