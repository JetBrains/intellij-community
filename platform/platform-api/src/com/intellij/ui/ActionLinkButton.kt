// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent

class ActionLinkButton(@Nls text: String, perform: (ActionEvent) -> Unit) : ActionLink(text, perform) {
  override fun getUIClassID(): String = "ButtonUI"
}