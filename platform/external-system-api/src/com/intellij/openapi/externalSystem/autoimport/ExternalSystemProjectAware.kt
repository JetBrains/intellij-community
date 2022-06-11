// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable

interface ExternalSystemProjectAware {

  val projectId: ExternalSystemProjectId

  /**
   * Collects settings files which will be watched.
   * This property can be called from any thread context to reduce UI freezes and CPU usage.
   * Result will be cached, so settings files should be equals between reloads.
   */
  val settingsFiles: Set<String>

  fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable)

  fun reloadProject(context: ExternalSystemProjectReloadContext)
}