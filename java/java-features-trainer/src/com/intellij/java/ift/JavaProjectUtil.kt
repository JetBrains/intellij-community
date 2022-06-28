// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookup
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision
import com.intellij.util.lang.JavaVersion

object JavaProjectUtil {
  fun findJavaSdkAsync(project: Project, onSdkSearchCompleted: (Sdk?) -> Unit) {
    val javaSdkType = JavaSdk.getInstance()
    SdkLookup.newLookupBuilder()
      .withProject(project)
      .withSdkType(javaSdkType)
      .withVersionFilter {
        JavaVersion.tryParse(it)?.isAtLeast(6) == true
      }
      .onDownloadableSdkSuggested {
        onSdkSearchCompleted(null)
        SdkLookupDecision.STOP
      }
      .onSdkResolved { sdk ->
        if (sdk != null && sdk.sdkType === javaSdkType) {
          onSdkSearchCompleted(sdk)
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