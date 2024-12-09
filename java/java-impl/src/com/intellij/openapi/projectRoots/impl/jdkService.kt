// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@ApiStatus.Internal
class AddJdkService(private val coroutineScope: CoroutineScope) {
  /**
   * Creates a JDK and calls [onJdkAdded] later if JDK creation was successful.
   * If you need to acquire a [Sdk] immediately, use [createIncompleteJdk].
   */
  fun createJdkFromPath(path: String, onJdkAdded: (Sdk) -> Unit = {}) {
    coroutineScope.launch {
      val jdk = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        SdkConfigurationUtil.createAndAddSDK(path, JavaSdk.getInstance())
      }
      if (jdk != null) onJdkAdded.invoke(jdk)
    }
  }

  /**
   * Creates and registers an incomplete JDK with a unique name pointing to the given [path].
   * Initiates the JDK setup in a coroutine.
   */
  fun createIncompleteJdk(path: String): Sdk? {
    val jdkType = JavaSdk.getInstance()
    val sdk = SdkConfigurationUtil.createIncompleteSDK(path, jdkType) ?: return null

    coroutineScope.launch {
      jdkType.setupSdkPaths(sdk)
    }

    return sdk
  }
}

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ConfigureJdkService(val project: Project, private val coroutineScope: CoroutineScope) {
  fun setProjectJdkIfNull(sdk: Sdk) {
    if (project.isDisposed) return
    val rootsManager = ProjectRootManager.getInstance(project)
    if (rootsManager.projectSdk == null) {
      coroutineScope.launch {
        writeAction {
          rootsManager.projectSdk = sdk
        }
      }
    }
  }
}