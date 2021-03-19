// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Implementors of this interface provide data that is used to check if the project is in the untrusted state now,
 * and if the IDE should notify the user about this fact.
 */
@ApiStatus.Experimental
interface UntrustedProjectModeProvider {

  /**
   * Returns the name of the build system corresponding to this provider.
   */
  val systemId: ProjectSystemId

  /**
   * Return true if the IDE should show a noticeable
   * [com.intellij.ui.EditorNotificationPanel permanent notification].
   */
  fun shouldShowEditorNotification(project: Project): Boolean

  fun loadAllLinkedProjects(project: Project)
}