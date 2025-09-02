// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.Icon

/**
 * Represents a suggestion displayed when there are no services in the Services tool window.
 * Enhances the UX for users opening the Services tool window for the first time.
 * @see ServiceViewContributor
 */
@ApiStatus.Internal
interface ServiceViewEmptyTreeSuggestion {
  val weight: Int

  val icon: Icon?

  @get:Nls(capitalization = Nls.Capitalization.Sentence)
  val text: String

  @get:NlsSafe
  val shortcutText: String?
    get() = null

  fun onActivate(dataContext: DataContext, inputEvent: InputEvent?)
}