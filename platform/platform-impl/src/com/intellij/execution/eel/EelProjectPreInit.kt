// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine
import com.intellij.platform.eel.provider.setEelMachine
import kotlinx.coroutines.CancellationException

/**
 * Associates the project with its [com.intellij.platform.eel.EelMachine] very early during project initialization,
 * before the IDE first touches the project's file system (e.g. during Workspace Model cache load).
 *
 * This intentionally does only a lightweight [resolveEelMachine] and does NOT deploy the agent
 * (IJent / Docker container / SSH connection): MultiRoutingFileSystem only needs the machine to be *resolved*
 * to route the project's paths. The actual deployment happens where it is allowed to do IO and pump the EDT:
 *  - [com.intellij.openapi.project.impl.ProjectManagerImpl] runs the side-effecting initializers for
 *    MultiRoutingFileSystem paths at the start of open;
 *  - environments that must deploy before opening (e.g. the RD thin client, whose agent port is forwarded over the
 *    EDT-bound RD protocol while project open suppresses EDT pumping) deploy it themselves before the project opens;
 *  - otherwise the agent is deployed lazily on the first real file-system request.
 *
 * Keeping this activity deploy-free is what lets it run uniformly for every project without deadlocking inside the
 * RD project-open window that suppresses EDT message pumping.
 */
internal class EelProjectPreInit : InitProjectActivity {
  override suspend fun run(project: Project) {
    if (project.isDefault) {
      return
    }

    val descriptor = project.getEelDescriptor()
    val machine = try {
      descriptor.resolveEelMachine()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.warn("Failed to resolve EelMachine for $descriptor", e)
      return
    }
    project.setEelMachine(machine)
  }

  companion object {
    private val LOG = logger<EelProjectPreInit>()
  }
}