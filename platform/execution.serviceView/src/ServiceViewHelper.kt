// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.execution.serviceView.splitApi.ServiceViewRpc
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.function.Consumer

@Service(Service.Level.PROJECT)
internal class ServiceViewHelper(private val project: Project, private val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ServiceViewHelper = project.service()
  }

  fun findServices(virtualFile: VirtualFile, callback: Consumer<Collection<String>>) {
    cs.launch {
      val services = ServiceViewRpc.getInstance().findServices(virtualFile.rpcId(), project.projectId())
      callback.accept(services)
    }
  }
}