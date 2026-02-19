// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.messages.Topic
import kotlin.time.Duration

object ExternalSystemTestUtil {
  @JvmField
  val TEST_EXTERNAL_SYSTEM_ID: ProjectSystemId = ProjectSystemId("TEST_EXTERNAL_SYSTEM_ID")

  @JvmField
  val SETTINGS_TOPIC: Topic<TestExternalSystemSettingsListener?> = Topic.create<TestExternalSystemSettingsListener?>(
    "TEST_EXTERNAL_SYSTEM_SETTINGS", TestExternalSystemSettingsListener::class.java
  )
}

@JvmOverloads
fun importData(dataNode: DataNode<ProjectData>, project: Project, timeout: Duration = DEFAULT_TEST_TIMEOUT) {
  timeoutRunBlocking(timeout) {
    ProjectDataManager.getInstance().importData<ProjectData?>(dataNode, project)
  }
}