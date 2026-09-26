// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.autoimport.ProjectAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.TestExternalSystemManager
import com.intellij.testFramework.ExtensionTestUtil

abstract class AutoReloadExternalSystemTestCase : AutoReloadTestCase() {

  protected fun testWithDummyExternalSystem(
    autoImportAwareCondition: Ref<Boolean>? = null,
    test: DummyExternalSystemTestBench.(VirtualFile) -> Unit,
  ) {
    val externalSystemManagers = ExternalSystemManager.EP_NAME.extensionList + TestExternalSystemManager(myProject)
    ExtensionTestUtil.maskExtensions(ExternalSystemManager.EP_NAME, externalSystemManagers, testRootDisposable)
    withProjectTracker {
      val systemId = ProjectSystemId("External System")
      val projectId = ExternalSystemProjectId(systemId, projectPath)
      val autoImportAware = object : ExternalSystemAutoImportAware {
        override fun getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String {
          return projectNioPath.getResolvedPath(SETTINGS_FILE).toCanonicalPath()
        }

        override fun isApplicable(resolverPolicy: ProjectResolverPolicy?): Boolean {
          return autoImportAwareCondition == null || autoImportAwareCondition.get()
        }
      }
      val file = findOrCreateFile(SETTINGS_FILE)
      val projectAware = ProjectAwareWrapper(ProjectAware(myProject, projectId, autoImportAware), it)
      register(projectAware, parentDisposable = it)
      DummyExternalSystemTestBench(projectAware).apply {
        assertStateAndReset(
          numReload = 1,
          numReloadStarted = 1,
          numReloadFinished = 1,
          event = "register project without cache"
        )

        test(file)
      }
    }
  }

  inner class DummyExternalSystemTestBench(
    private val projectAware: ProjectAwareWrapper,
  ) {

    fun assertStateAndReset(
      numReload: Int,
      numReloadStarted: Int? = null,
      numReloadFinished: Int? = null,
      numSubscribing: Int? = null,
      numUnsubscribing: Int? = null,
      autoReloadType: AutoReloadType? = null,
      event: String,
    ) {
      assertProjectTrackerSettings(autoReloadType, event)

      assertCountEventAndReset(projectAware.projectId, numReload, projectAware.reloadCounter, "project reload", event)
      assertCountEventAndReset(projectAware.projectId, numReloadStarted, projectAware.startReloadCounter, "project before reload", event)
      assertCountEventAndReset(projectAware.projectId, numReloadFinished, projectAware.finishReloadCounter, "project after reload", event)
      assertCountEventAndReset(projectAware.projectId, numSubscribing, projectAware.subscribeCounter, "subscribe", event)
      assertCountEventAndReset(projectAware.projectId, numUnsubscribing, projectAware.unsubscribeCounter, "unsubscribe", event)
    }
  }
}