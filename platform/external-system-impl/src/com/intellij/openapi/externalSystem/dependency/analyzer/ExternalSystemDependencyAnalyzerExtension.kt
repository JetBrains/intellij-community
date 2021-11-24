// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project


class ExternalSystemDependencyAnalyzerExtension : DependencyAnalyzerExtension {
  override fun getContributor(project: Project, systemId: ProjectSystemId): DependencyContributor? {
    ExternalSystemApiUtil.getManager(systemId) ?: return null
    return object : DummyDependencyContributor(project) {
      override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
        val progressManager = ExternalSystemProgressNotificationManager.getInstance()
        progressManager.addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
          override fun onEnd(id: ExternalSystemTaskId) {
            if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
            if (id.projectSystemId != systemId) return
            listener()
          }
        }, parentDisposable)
      }
    }
  }
}