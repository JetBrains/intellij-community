// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.openapi.externalSystem.service.execution

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.*
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * The initial naming of the method is wrong, the method has nothing to do with "non-blocking execution".
 * The method could be suspendable without any side-effects.
 * The original intention of the "non-blocking" is to show that the code inside shouldn't download JDK and execute
 * any heavy and long operations inside.
 * The actual resolution of JDK would be performed when a Gradle execution would be started.
 */
@Deprecated("Use resolveJdkInfo instead")
fun SdkLookupProvider.nonblockingResolveJdkInfo(projectSdk: Sdk?, jdkReference: String?): SdkInfo {
  return runBlockingCancellable {
    resolveJdkInfo(null, projectSdk, jdkReference)
  }
}

suspend fun SdkLookupProvider.resolveJdkInfo(project: Project?, projectSdk: Sdk?, jdkReference: String?): SdkInfo {
  return when (jdkReference) {
    USE_JAVA_HOME -> resolveJavaHomeJdkInfo(project)
    USE_PROJECT_JDK -> resolveProjectJdkInfo(projectSdk)
    USE_INTERNAL_JAVA -> createSdkInfo(getInternalJdk())
    else -> resolveSdkInfoBySdkName(jdkReference)
  }
}

private suspend fun SdkLookupProvider.resolveJavaHomeJdkInfo(project: Project?): SdkInfo {
  val eelDescriptor = project?.getEelDescriptor() ?: LocalEelDescriptor
  val eel = eelDescriptor.upgrade()
  val environment = eel.exec.fetchLoginShellEnvVariables()
  val jdkPathEnvValue = environment[JAVA_HOME]
  if (jdkPathEnvValue == null) {
    return SdkInfo.Undefined
  }
  val jdkPath = eel.fs.getPath(jdkPathEnvValue)
    .asNioPath()
    .toString()
  return createJdkInfo(JAVA_HOME, jdkPath)
}

private fun getInternalJdk(): Sdk {
  return ExternalSystemJdkProvider.getInstance().internalJdk
}

private fun SdkLookupProvider.resolveProjectJdkInfo(projectSdk: Sdk?): SdkInfo {
  if (projectSdk == null) return SdkInfo.Undefined
  val resolvedSdk = resolveDependentJdk(projectSdk)
  return resolveSdkInfo(resolvedSdk)
}

private fun SdkLookupProvider.resolveSdkInfo(sdk: Sdk?): SdkInfo {
  if (sdk == null) return SdkInfo.Undefined
  executeSdkLookup(sdk)
  return getSdkInfo()
}

private fun SdkLookupProvider.resolveSdkInfoBySdkName(sdkName: String?): SdkInfo {
  if (sdkName == null) return getSdkInfo()
  executeSdkLookup(sdkName)
  return getSdkInfo()
}

fun SdkLookupProvider.nonblockingResolveSdkBySdkName(sdkName: String?): Sdk? {
  if (sdkName == null) return getSdk()
  // This lookup may run away to async behaviour if a given SDK is downloading,
  // sdkName is sent back faster thou
  executeSdkLookup(sdkName)
  return getSdk()
}

private fun SdkLookupProvider.executeSdkLookup(sdk: Sdk?) {
  newLookupBuilder()
    .testSuggestedSdkFirst { sdk }
    .onBeforeSdkSuggestionStarted { SdkLookupDecision.STOP }
    .executeLookup()
}

private fun SdkLookupProvider.executeSdkLookup(sdkName: String) {
  newLookupBuilder()
    .withSdkName(sdkName)
    .onBeforeSdkSuggestionStarted { SdkLookupDecision.STOP }
    .executeLookup()
}

fun createJdkInfo(name: String, homePath: String?): SdkInfo {
  if (homePath == null) return SdkInfo.Undefined
  val type = getJavaSdkType()
  val versionString = type.getVersionString(homePath)
  return SdkInfo.Resolved(name, versionString, homePath)
}

fun createSdkInfo(sdk: Sdk): SdkInfo {
  return SdkInfo.Resolved(sdk.name, sdk.versionString, sdk.homePath)
}
