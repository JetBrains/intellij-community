// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.edtWriteAction
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
public class AddJdkService(private val coroutineScope: CoroutineScope) {
  /**
   * Creates a JDK and calls [onJdkAdded] later if JDK creation was successful.
   * If you need to acquire a [Sdk] immediately, use [createIncompleteJdk].
   */
  public fun createJdkFromPath(path: String, onJdkAdded: (Sdk) -> Unit = {}) {
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
  public fun createIncompleteJdk(path: String): Sdk? {
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
public class ConfigureJdkService(public val project: Project, private val coroutineScope: CoroutineScope) {
  public fun setProjectJdkIfNull(sdk: Sdk, notify: Boolean = false) {
    if (project.isDisposed) return
    val rootsManager = ProjectRootManager.getInstance(project)
    if (rootsManager.projectSdk == null) {
      coroutineScope.launch {
        edtWriteAction {
          rootsManager.projectSdk = sdk
        }

        if (notify) {
          NotificationGroupManager.getInstance()
            .getNotificationGroup("Setup JDK")
            .createNotification(
              JavaBundle.message("sdk.configured.notification.title"),
              JavaBundle.message("sdk.configured", sdk.name),
              NotificationType.INFORMATION
            )
            .notify(project)
        }
      }
    }
  }
}