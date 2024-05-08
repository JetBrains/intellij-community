@file:Internal
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
internal class ProjectViewInitNotifier(private val project: Project) {
  private val startedOccurred = AtomicBoolean()
  private val cachedNodesLoadedOccurred = AtomicBoolean()
  private val completedOccurred = AtomicBoolean()

  fun initStarted() {
    if (!startedOccurred.compareAndSet(false, true)) return
    MY_LOG.info("Project View initialization started")
    project.messageBus.syncPublisher(ProjectViewListener.TOPIC).initStarted()
  }

  fun initCachedNodesLoaded() {
    if (!cachedNodesLoadedOccurred.compareAndSet(false, true)) return
    MY_LOG.info("Project View cached nodes loaded")
    project.messageBus.syncPublisher(ProjectViewListener.TOPIC).initCachedNodesLoaded()
  }

  fun initCompleted() {
    if (!completedOccurred.compareAndSet(false, true)) return
    MY_LOG.info("Project View initialization completed")
    project.messageBus.syncPublisher(ProjectViewListener.TOPIC).initCompleted()
  }
}

private val MY_LOG = logger<ProjectViewInitNotifier>()
