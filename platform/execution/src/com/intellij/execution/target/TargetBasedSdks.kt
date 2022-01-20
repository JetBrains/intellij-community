// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetBasedSdks")

package com.intellij.execution.target

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.configurationStore.jdomSerializer
import com.intellij.execution.target.ContributedConfigurationsList.Companion.getSerializer
import com.intellij.execution.target.TargetEnvironmentsManager.OneTargetState.Companion.toOneTargetState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

private const val TARGET_ENVIRONMENT_CONFIGURATION = "targetEnvironmentConfiguration"

private val LOG = Logger.getInstance("#" + "com.intellij.execution.target.TargetBasedSdks")

fun Sdk.isBasedOnTargetType(targetTypeId: String): Boolean =
  (sdkAdditionalData as? TargetBasedSdkAdditionalData)?.targetEnvironmentConfiguration?.getTargetType()?.id == targetTypeId

fun TargetBasedSdkAdditionalData.getTargetEnvironmentRequest(project: Project?): TargetEnvironmentRequest? {
  return targetEnvironmentConfiguration?.createEnvironmentRequest(project ?: ProjectManager.getInstance().defaultProject)
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

/**
 * @deprecated
 * @param element the "additional" element of IntelliJ SDK data
 */
@Deprecated(message = "replace with loadTargetConfiguration")
fun loadTargetBasedSdkAdditionalData(element: Element): Pair<ContributedConfigurationsList.ContributedStateBase?, TargetEnvironmentConfiguration?> {
  // the state that contains information of the target, as for now the target configuration is embedded into the additional data
  val targetConfigurationElement = element.getChild(TARGET_ENVIRONMENT_CONFIGURATION)
  if (targetConfigurationElement == null) {
    LOG.warn("SDK target configuration data is absent")
    return null to null
  }
  val targetState = jdomSerializer.deserialize(targetConfigurationElement, ContributedConfigurationsList.ContributedStateBase::class.java)
  val loadedConfiguration = fromOneState(targetState)
  if (loadedConfiguration == null) {
    LOG.info("Cannot load SDK target configuration data")
  }
  return targetState to loadedConfiguration
}

fun saveTargetConfiguration(element: Element, config: TargetEnvironmentConfiguration?) {
  val targetStateElement = Element(TARGET_ENVIRONMENT_CONFIGURATION).also {
    element.addContent(it)
  }

  config?.toOneTargetState()?.let {
    jdomSerializer.serializeObjectInto(it, targetStateElement)
  }
}

fun loadTargetConfiguration(element: Element): TargetEnvironmentConfiguration? {
  val targetConfigurationElement = element.getChild(TARGET_ENVIRONMENT_CONFIGURATION) ?: return null
  val targetState = jdomSerializer.deserialize(targetConfigurationElement, TargetEnvironmentsManager.OneTargetState::class.java)
  return targetState.toTargetConfiguration()
}

/**
 * @see com.intellij.execution.target.ContributedConfigurationsList.fromOneState
 */
private fun fromOneState(state: ContributedConfigurationsList.ContributedStateBase): TargetEnvironmentConfiguration? {
  val type = TargetEnvironmentType.EXTENSION_NAME.extensionList.firstOrNull { it.id == state.typeId }
  val defaultConfig = type?.createDefaultConfig()
  return defaultConfig?.also {
    it.displayName = state.name ?: ""
    ComponentSerializationUtil.loadComponentState(it.getSerializer(), state.innerState)
  }
}