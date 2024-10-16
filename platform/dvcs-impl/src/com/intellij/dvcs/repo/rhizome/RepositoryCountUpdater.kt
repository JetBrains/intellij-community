// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo.rhizome

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.vcs.impl.shared.rhizome.RepositoryCountEntity
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RepositoryCountUpdater(private val project: Project, private val cs: CoroutineScope) : VcsRepositoryMappingListener {

  override fun mappingChanged() {
    cs.launch {
      withKernel {
        change {
          shared {
            RepositoryCountEntity.upsert(RepositoryCountEntity.Project, project.asEntity()) {
              it[RepositoryCountEntity.Count] = RepositoryChangesBrowserNode.getColorManager(project).paths.size
            }
          }
        }
      }
    }
  }
}