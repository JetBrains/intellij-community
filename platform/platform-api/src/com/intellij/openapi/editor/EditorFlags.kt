// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EditorFlags")
@file:Internal

package com.intellij.openapi.editor

import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus.Internal

val isLocalEditorUx: Boolean
  get() = Registry.`is`("editor.rd.local.ux")

fun isMonolith(): Boolean {
  return isLocalEditorUx || AppModeAssertions.isMonolith()
}
