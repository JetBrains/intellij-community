// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo.rhizome

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.platform.vcs.impl.shared.rhizome.RepositoryCountEntity
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RepositoryCountUpdater(private val project: Project, private val cs: CoroutineScope) : VcsRepositoryMappingListener {

  private val updateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

  override fun mappingChanged() {
    cs.launch {
      updateSemaphore.withPermit {
        withKernel {
          val projectEntity = project.asEntity()
          change {
            shared {
              RepositoryCountEntity.upsert(RepositoryCountEntity.Project, projectEntity) {
                it[RepositoryCountEntity.Count] = RepositoryChangesBrowserNode.getColorManager(project).paths.size
              }
            }
          }
        }
      }
    }
  }
}