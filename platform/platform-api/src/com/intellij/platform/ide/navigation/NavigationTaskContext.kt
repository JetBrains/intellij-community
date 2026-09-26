// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * UI-thread snapshot required to submit navigation from any thread afterward.
 * Do not read focus or [ModalityState.current] when constructing this off the EDT.
 *
 * @see [com.intellij.platform.ide.navigation.toNavigationOptions].
 */
@ApiStatus.Internal
class NavigationTaskContext internal constructor(
  val dataContext: DataContext?,
  val modalityState: ModalityState,
  private val clientIdContext: CoroutineContext,
  private val requestedOptions: NavigationOptions,
) {
  val coroutineContext: CoroutineContext
    get() = clientIdContext + modalityState.asContextElement()

  val navigationOptions: NavigationOptions by lazy {
    dataContext.toNavigationOptions(requestedOptions)
  }
}
