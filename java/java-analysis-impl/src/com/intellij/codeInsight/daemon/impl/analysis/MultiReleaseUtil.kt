// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MultiReleaseUtil")
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.model.serialization.SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
import java.util.regex.Pattern

private const val MAIN = "main"
private val javaVersionPattern: Pattern by lazy { Pattern.compile("java\\d+") }

@Internal
fun areMainAndAdditionalMultiReleaseModules(mainModule: Module, additionalModule: Module): Boolean {
  // Maven
  val project = additionalModule.project
  val storage = project.workspaceModel.currentSnapshot
  val additionalModuleName = additionalModule.name
  val mainModuleName = mainModule.name
  val additionalModuleExOptions = storage.resolve(ModuleId(additionalModuleName))?.exModuleOptions
  val mainModuleExOptions = storage.resolve(ModuleId(mainModuleName))?.exModuleOptions
  if (additionalModuleExOptions?.externalSystem == MAVEN_EXTERNAL_SOURCE_ID
      && mainModuleExOptions?.externalSystem == MAVEN_EXTERNAL_SOURCE_ID) {
    val baseModuleName = mainModuleName.substringBeforeLast('.')
    if (additionalModuleName.startsWith(baseModuleName)
        && additionalModuleExOptions.externalSystemModuleType == "MAIN_ONLY_ADDITIONAL" // StandardMavenModuleType.MAIN_ONLY_ADDITIONAL
        && mainModuleExOptions.externalSystemModuleType == "MAIN_ONLY" // StandardMavenModuleType.MAIN_ONLY
    ) {
      return true
    }
  }

  // Gradle
  if (mainModuleName.endsWith(".$MAIN")) {
    val baseModuleName = mainModuleName.substringBeforeLast(MAIN)
    return javaVersionPattern.matcher(additionalModuleName.substringAfter(baseModuleName)).matches()
  }
  return false
}

@Internal
fun getMainMultiReleaseModule(additionalModule: Module): Module? {
  // Maven
  val project = additionalModule.project
  val storage = project.workspaceModel.currentSnapshot
  val additionalModuleName = additionalModule.name
  val additionalModuleExOptions = storage.resolve(ModuleId(additionalModuleName))?.exModuleOptions
  if (additionalModuleExOptions?.externalSystem == MAVEN_EXTERNAL_SOURCE_ID) {
    val baseModuleName = additionalModuleName.substringBeforeLast('.')
    val mainModuleName = "$baseModuleName.$MAIN"
    val mainModuleExOptions = storage.resolve(ModuleId(mainModuleName))?.exModuleOptions
    if (mainModuleExOptions?.externalSystemModuleType == "MAIN_ONLY" // StandardMavenModuleType.MAIN_ONLY
    ) {
      return ModuleManager.getInstance(project).findModuleByName(mainModuleName)
    }
  }
  return null
}