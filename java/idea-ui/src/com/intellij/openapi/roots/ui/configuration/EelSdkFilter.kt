// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelSdkFilter")

package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.platform.eel.provider.getEelApiKey
import java.util.function.Predicate

internal fun filterSdkByEel(project: Project): Predicate<Sdk> {
  val eelApiKey = project.getEelApiKey()
  return Predicate { sdk ->
    ProjectSdksModel.sdkMatchesEel(eelApiKey, sdk)
  }
}

internal fun filterSdkSuggestionByEel(project: Project): Predicate<SdkListItem.SuggestedItem> {
  val eelApiKey = project.getEelApiKey()
  return Predicate { item ->
    ProjectSdksModel.sdkMatchesEel(eelApiKey, item.homePath)
  }
}