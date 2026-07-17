// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.actions

import com.intellij.codeInsight.hint.ImplementationViewSession
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PeekDefinitionAction : ShowImplementationsAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun showImplementations(
    session: ImplementationViewSession,
    invokedFromEditor: Boolean,
    invokedByShortcut: Boolean,
  ) {
    if (PeekDefinitionManager.show(session)) {
      triggerFeatureUsed(session.project)
    }
    else {
      super.showImplementations(session, invokedFromEditor, invokedByShortcut)
    }
  }
}
