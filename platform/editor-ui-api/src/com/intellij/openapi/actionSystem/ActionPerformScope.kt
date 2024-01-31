// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Specifies whether the action execution should be canceled on context [com.intellij.openapi.project.Project] closing.
 */
@Experimental
enum class ActionPerformScope {

  /**
   * Action execution is canceled on the application closing, but not canceled on the project closing.
   */
  APPLICATION,

  /**
   * Action execution is canceled on the project closing if there is a project in data context.
   */
  PROJECT,

  /**
   * If the action requests [a project][CommonDataKeys.PROJECT] from [DataContext] during [AnAction.update],
   * then [AnAction.actionPerformed] is run with [PROJECT] scope, otherwise it is run with [APPLICATION] scope.
   *
   * Sometimes this is not enough, for example, an action, which closes the current project,
   * has to request the project from the data context, but the execution of the action itself should not be canceled.
   * In this case it's possible to override [AnAction.getActionPerformScope] and specify [APPLICATION] explicitly.
   *
   * The inverse is also possible. If the action does not request the current project from the data context during action update,
   * but the action should be canceled on project closing, it is also possible to override [AnAction.getActionPerformScope]
   * and specify [PROJECT] explicitly.
   *
   * TODO this is unsupported
   */
  GUESS,
}
