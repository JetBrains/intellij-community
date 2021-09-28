// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.impl.DocumentationHistory
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.ui.popup.JBPopup

@JvmField
val DOCUMENTATION_TARGETS_KEY: DataKey<List<DocumentationTarget>> = DataKey.create("documentation.targets")
internal val DOCUMENTATION_HISTORY_DATA_KEY: DataKey<DocumentationHistory> = DataKey.create("documentation.history")
internal val DOCUMENTATION_TARGET_POINTER_KEY: DataKey<Pointer<out DocumentationTarget>> = DataKey.create("documentation.target.pointer");
internal val DOCUMENTATION_POPUP_KEY: DataKey<JBPopup> = DataKey.create("documentation.popup")

internal const val TOGGLE_SHOW_IN_POPUP_ACTION_ID: String = "Documentation.ToggleShowInPopup"
internal const val TURN_OFF_AUTO_UPDATE_ACTION_ID: String = "Documentation.TurnOffAutoUpdate"

// TODO ? register these actions in XML
internal fun primaryActions(): List<AnAction> = listOf(
  DocumentationBackAction(),
  DocumentationForwardAction(),
  DocumentationEditSourceAction(),
)
