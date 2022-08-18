// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow


class NavBarService(val myProject: Project) : Disposable {

  private val cs = CoroutineScope(CoroutineName("NavigationBarStore"))
  private val myEventFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val navigationBar = NavigationBar(myProject, cs, myEventFlow.asSharedFlow())

  init {
    IdeEventQueue.getInstance().addActivityListener(Runnable {
      myEventFlow.tryEmit(Unit)
    }, this)
  }

  override fun dispose() {
    cs.coroutineContext.cancel()
  }

  fun getPanel() = navigationBar.getComponent()

}
