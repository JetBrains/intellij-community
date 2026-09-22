// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Opens the New Project wizard of the current product.
 *
 * Every product names its own action, so a caller outside the product must not use an action id.
 * Call [performNewProjectAction] instead.
 *
 * The service resolves the action on every call. A product can replace the action at run time.
 */
@ApiStatus.Internal
interface NewProjectEntryPoint {

  /**
   * Returns the New Project action of the current product, or `null` when the product registers none.
   */
  fun findNewProjectAction(): AnAction?

  /**
   * Shows the New Project wizard of the current product.
   *
   * @param dataContext the context of the caller, for example the context of the dialog that holds the button.
   * Put [PROJECT_LOCATION] into it to seed the Location field of the wizard.
   */
  fun performNewProjectAction(dataContext: DataContext)

  companion object {
    /**
     * The directory that the Location field of the wizard shows at start.
     * The wizard creates the project in a subdirectory of it.
     * When the key is absent, the wizard uses the location that [com.intellij.ide.RecentProjectsManager] suggests.
     */
    @JvmField
    val PROJECT_LOCATION: DataKey<Path> = DataKey.create("NewProjectEntryPoint.projectLocation")

    @JvmStatic
    fun getInstance(): NewProjectEntryPoint = service()
  }
}
