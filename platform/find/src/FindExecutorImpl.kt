// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.impl.FindExecutor
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.usages.UsageInfoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class FindExecutorImpl(val coroutineScope: CoroutineScope): FindExecutor {
  override fun findUsages(project: Project, findModel: FindModel, previousUsages: Set<UsageInfoAdapter>, onResult: (UsageInfoAdapter) -> Boolean, onFinish: () -> Unit?) {
    coroutineScope.launch {
      val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.model?.fileId?.virtualFile() }.toSet()

      //TODO provide custom scope and search context (it's not serializable in FindModel)
      FindRemoteApi.getInstance().findByModel(findModel, project.projectId(), filesToScanInitially.map { it.rpcId() }).collect { findResult ->
        val usage = UsageInfoModel(project, findResult, coroutineScope)
        onResult(usage)
      }
      onFinish()
    }
  }
}