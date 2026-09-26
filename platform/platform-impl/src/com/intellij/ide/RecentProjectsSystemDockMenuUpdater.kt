// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.runActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.createMacDelegate
import com.intellij.ui.win.createWinDockDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<RecentProjectsSystemDockMenuUpdater>()

@OptIn(FlowPreview::class)
internal class RecentProjectsSystemDockMenuUpdater(private val coroutineScope: CoroutineScope) {
  private val started = AtomicBoolean()
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun start() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || !started.compareAndSet(false, true)) {
      return
    }

    coroutineScope.launch {
      val delegate = when {
                       SystemInfoRt.isMac -> createMacDelegate()
                       SystemInfoRt.isWindows -> createWinDockDelegate()
                       else -> null
                     } ?: return@launch

      updateRequests
        .debounce(50.milliseconds)
        .collectLatest {
          runActivity("system dock menu") {
            runCatching {
              delegate.updateRecentProjectsMenu()
            }.getOrLogException(LOG)
          }
        }
    }

    requestUpdate()
  }

  fun requestUpdate() {
    check(updateRequests.tryEmit(Unit))
  }
}

internal class SystemDockMenuPostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    (serviceAsync<RecentProjectsManager>() as? RecentProjectsManagerBase)?.startSystemDockUpdates()
  }
}
