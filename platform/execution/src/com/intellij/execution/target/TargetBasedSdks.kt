// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TargetBasedSdks")

package com.intellij.execution.target

import com.intellij.configurationStore.jdomSerializer
import com.intellij.execution.target.TargetEnvironmentsManager.OneTargetState.Companion.toOneTargetState
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.xmlb.JdomAdapter
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

private const val TARGET_ENVIRONMENT_CONFIGURATION = "targetEnvironmentConfiguration"

fun Sdk.isBasedOnTargetType(targetTypeId: String): Boolean =
  (sdkAdditionalData as? TargetBasedSdkAdditionalData)?.targetEnvironmentConfiguration?.getTargetType()?.id == targetTypeId

fun TargetBasedSdkAdditionalData.getTargetEnvironmentRequest(project: Project?): TargetEnvironmentRequest? {
  return targetEnvironmentConfiguration?.createEnvironmentRequest(project)
}

/**
 * @deprecated
 * @param element the "additional" element of IntelliJ SDK data to store state to
 * @param targetState the state that contains target configuration to be stored
 */
@Deprecated(message = "replace with saveTargetConfiguration")
fun saveTargetBasedSdkAdditionalData(element: Element, targetState: ContributedConfigurationsList.ContributedStateBase?) {
  val targetStateElement = Element(TARGET_ENVIRONMENT_CONFIGURATION)
  element.addContent(targetStateElement)
  targetState?.let { XmlSerializer.serializeInto(it, targetStateElement) }
}

fun saveTargetConfiguration(element: Element, config: TargetEnvironmentConfiguration?) {
  val targetStateElement = Element(TARGET_ENVIRONMENT_CONFIGURATION).also {
    element.addContent(it)
  }

  config?.toOneTargetState()?.let {
    jdomSerializer.serializeObjectInto(it, targetStateElement)
  }
}

fun hasTargetConfiguration(element: Element): Boolean {
  return element.getChild(TARGET_ENVIRONMENT_CONFIGURATION) != null
}

fun loadTargetConfiguration(element: Element): TargetEnvironmentConfiguration? {
  val targetConfigurationElement = element.getChild(TARGET_ENVIRONMENT_CONFIGURATION) ?: return null
  val targetState = jdomSerializer.deserialize(targetConfigurationElement, TargetEnvironmentsManager.OneTargetState::class.java, JdomAdapter)
  return targetState.toTargetConfiguration()
}