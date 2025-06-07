// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelSdkFilter")

package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.platform.eel.provider.getEelDescriptor
import java.util.function.Predicate

internal fun filterSdkByEel(project: Project): Predicate<Sdk> {
  val eelDescriptor = project.getEelDescriptor()
  return Predicate { sdk ->
    ProjectSdksModel.sdkMatchesEel(eelDescriptor, sdk)
  }
}

internal fun filterSdkSuggestionByEel(project: Project): Predicate<SdkListItem.SuggestedItem> {
  val eelDescriptor = project.getEelDescriptor()
  return Predicate { item ->
    ProjectSdksModel.sdkMatchesEel(eelDescriptor, item.homePath)
  }
}