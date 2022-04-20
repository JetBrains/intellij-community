// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookup
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision
import com.intellij.openapi.ui.Messages
import com.intellij.util.lang.JavaVersion
import training.learn.LearnBundle
import training.project.ProjectUtils

object JavaProjectUtil {
  fun findJavaSdkAsync(project: Project, onSdkSearchCompleted: (Sdk?) -> Unit) {
    val javaSdkType = JavaSdk.getInstance()
    val notification = ProjectUtils.createSdkDownloadingNotification()
    SdkLookup.newLookupBuilder()
      .withSdkType(javaSdkType)
      .withVersionFilter {
        JavaVersion.tryParse(it)?.isAtLeast(6) == true
      }
      .onDownloadableSdkSuggested { sdkFix ->
        val userDecision = invokeAndWaitIfNeeded {
          Messages.showYesNoDialog(
            LearnBundle.message("learn.project.initializing.jdk.download.message", sdkFix.downloadDescription),
            LearnBundle.message("learn.project.initializing.jdk.download.title"),
            null
          )
        }
        if (userDecision == Messages.YES) {
          notification.notify(project)
          SdkLookupDecision.CONTINUE
        }
        else {
          onSdkSearchCompleted(null)
          SdkLookupDecision.STOP
        }
      }
      .onSdkResolved { sdk ->
        if (sdk != null && sdk.sdkType === javaSdkType) {
          onSdkSearchCompleted(sdk)
          DumbService.getInstance(project).runWhenSmart {
            notification.expire()
          }
        }
      }
      .executeLookup()
  }

  fun getProjectJdk(project: Project): Sdk? {
    val projectJdk = ProjectRootManager.getInstance(project).projectSdk
    val module = ModuleManager.getInstance(project).modules.first()
    val moduleJdk = ModuleRootManager.getInstance(module).sdk
    return moduleJdk ?: projectJdk
  }
}