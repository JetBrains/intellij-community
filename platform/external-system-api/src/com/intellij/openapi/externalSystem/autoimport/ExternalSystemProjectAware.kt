// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemProjectAware {

  val projectId: ExternalSystemProjectId

  /**
   * Collects settings files that are be watched
   * This function is called on background thread in read action
   * Please use [com.intellij.openapi.progress.ProgressManager.checkCanceled] while collecting
   *
   * @see [ReadAction.nonBlocking]
   */
  val settingsFiles: Set<String>

  fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable)

  fun reloadProject(context: ExternalSystemProjectReloadContext)
}