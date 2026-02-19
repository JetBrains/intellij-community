// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ActionRemoteBehaviorCustomizer {
  open fun customizeActionUpdateBehavior(action: AnAction, behavior: ActionRemoteBehavior?): ActionRemoteBehavior? {
    return behavior
  }
}
