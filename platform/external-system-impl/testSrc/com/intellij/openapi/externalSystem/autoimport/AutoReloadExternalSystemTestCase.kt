// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType.SELECTIVE
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy
import com.intellij.openapi.externalSystem.service.project.autoimport.ProjectAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
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
      val projectId = ExternalSystemProjectId(TEST_EXTERNAL_SYSTEM_ID, projectPath)
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

    private fun resetAssertionCounters() {
      projectAware.resetAssertionCounters()
    }

    fun assertStateAndReset(
      numReload: Int? = null,
      numReloadStarted: Int? = null,
      numReloadFinished: Int? = null,
      numSubscribing: Int? = null,
      numUnsubscribing: Int? = null,
      autoReloadType: AutoReloadType = SELECTIVE,
      event: String,
    ) {
      assertState(numReload, numReloadStarted, numReloadFinished, numSubscribing, numUnsubscribing, autoReloadType, event)
      resetAssertionCounters()
    }

    private fun assertState(
      numReload: Int? = null,
      numReloadStarted: Int? = null,
      numReloadFinished: Int? = null,
      numSubscribing: Int? = null,
      numUnsubscribing: Int? = null,
      autoReloadType: AutoReloadType = SELECTIVE,
      event: String,
    ) {
      if (numReload != null) assertCountEvent(numReload, projectAware.reloadCounter.get(), "project reload", event)
      if (numReloadStarted != null) assertCountEvent(numReloadStarted, projectAware.startReloadCounter.get(), "project before reload", event)
      if (numReloadFinished != null) assertCountEvent(numReloadFinished, projectAware.finishReloadCounter.get(), "project after reload", event)
      if (numSubscribing != null) assertCountEvent(numSubscribing, projectAware.subscribeCounter.get(), "subscribe", event)
      if (numUnsubscribing != null) assertCountEvent(numUnsubscribing, projectAware.unsubscribeCounter.get(), "unsubscribe", event)
      assertProjectTrackerSettings(autoReloadType, event = event)
    }
  }
}