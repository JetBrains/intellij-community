// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.documentation

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Facade for consumption in actions.
 * Use [DOCUMENTATION_BROWSER] to obtain the current instance.
 * The instance must not be stored in a field or elsewhere.
 */
@Experimental
@NonExtendable
interface DocumentationBrowserFacade {

  /**
   * Pointer to the currently open target.
   */
  val targetPointer: Pointer<out DocumentationTarget>

  /**
   * Queues reloading from the current target.
   * Does not change back/forward history stacks.
   */
  fun reload()
}
