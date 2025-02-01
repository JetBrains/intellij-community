// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MultiReleaseUtil")
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.model.serialization.SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
import java.util.regex.Pattern

private const val MAIN = "main"
private val javaVersionPattern: Pattern by lazy { Pattern.compile("java\\d+") }

@Internal
fun inSameMultiReleaseModule(mainModule: Module, additionalModule: Module): Boolean {
  // Maven
  val project = additionalModule.project
  val storage = project.workspaceModel.currentSnapshot
  val placeModuleName = additionalModule.name
  val targetModuleName = mainModule.name
  val placeModuleExOptions = storage.resolve(ModuleId(placeModuleName))?.exModuleOptions
  val targetModuleExOptions = storage.resolve(ModuleId(targetModuleName))?.exModuleOptions
  if (placeModuleExOptions?.externalSystem == MAVEN_EXTERNAL_SOURCE_ID
      && targetModuleExOptions?.externalSystem == MAVEN_EXTERNAL_SOURCE_ID) {
    val baseModuleName = targetModuleName.substringBeforeLast('.')
    if (placeModuleName.startsWith(baseModuleName)
        && placeModuleExOptions.externalSystemModuleType == "MAIN_ONLY_ADDITIONAL" // StandardMavenModuleType.MAIN_ONLY_ADDITIONAL
        && targetModuleExOptions.externalSystemModuleType == "MAIN_ONLY" // StandardMavenModuleType.MAIN_ONLY
    ) {
      return true
    }
  }

  // Gradle
  if (targetModuleName.endsWith(".$MAIN")) {
    val baseModuleName = targetModuleName.substringBeforeLast(MAIN)
    return javaVersionPattern.matcher(placeModuleName.substringAfter(baseModuleName)).matches()
  }
  return false
}