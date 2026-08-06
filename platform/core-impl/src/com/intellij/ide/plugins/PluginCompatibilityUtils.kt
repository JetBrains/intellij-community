// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.isIgnoreCompatibility
import com.intellij.ide.plugins.PluginManagerCore.logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginCompatibilityUtils {
  // skip our plugins as expected to be up to date whether bundled or not
  fun isLegacyPluginWithoutPlatformAliasDependencies(descriptor: IdeaPluginDescriptorImpl): Boolean {
    return !descriptor.isBundled &&
           descriptor.packagePrefix == null &&
           !descriptor.isImplementationDetail &&
           descriptor.contentModules.isEmpty() &&
           descriptor.moduleDependencies.modules.isEmpty() &&
           descriptor.moduleDependencies.plugins.isEmpty() &&
           descriptor.pluginId != PluginManagerCore.CORE_ID &&
           descriptor.pluginId != PluginManagerCore.JAVA_PLUGIN_ID &&
           !hasJavaOrPlatformAliasDependency(descriptor)
  }

  private fun hasJavaOrPlatformAliasDependency(descriptor: IdeaPluginDescriptorImpl): Boolean {
    for (dependency in descriptor.dependencies) {
      val dependencyPluginId = dependency.pluginId
      if (PluginManagerCore.JAVA_PLUGIN_ID == dependencyPluginId ||
          PluginManagerCore.JAVA_PLUGIN_ALIAS_ID == dependencyPluginId ||
          PluginManagerCore.looksLikePlatformPluginAlias(dependencyPluginId)) {
        return true
      }
    }
    return false
  }

  /** temporary migration helper */
  fun PluginIncompatibilityReason.convertToUIError(descriptor: IdeaPluginDescriptorImpl): PluginNonLoadReason {
    return when (this) {
      is PluginIncompatibilityReason.IncompatibleWithCpuArch -> PluginIsIncompatibleWithHostCpu(descriptor, requiredArch, hostArch)
      is PluginIncompatibilityReason.IncompatibleWithHostPlatform -> PluginIsIncompatibleWithHostPlatform(descriptor, requiredOS, hostOS.name)
      PluginIncompatibilityReason.MalformedSinceUntilConstraints -> PluginMalformedSinceUntilConstraints(descriptor)
      is PluginIncompatibilityReason.SinceBuildConstraintViolation -> PluginSinceBuildConstraintViolation(descriptor, productBuildNumber)
      is PluginIncompatibilityReason.UntilBuildConstraintViolation -> PluginUntilBuildConstraintViolation(descriptor, productBuildNumber)
    }
  }

  @JvmStatic
  @OptIn(LowLevelLocalMachineAccess::class)
  fun checkBuildNumberCompatibility(descriptor: IdeaPluginDescriptor, ideBuildNumber: BuildNumber): PluginIncompatibilityReason? {
    val requiredOs = getUnfulfilledOsRequirement(descriptor)
    if (requiredOs != null) {
      return PluginIncompatibilityReason.IncompatibleWithHostPlatform(requiredOs, OS.CURRENT)
    }

    val requiredArch = getUnfulfilledCpuArchRequirement(descriptor)
    if (requiredArch != null) {
      return PluginIncompatibilityReason.IncompatibleWithCpuArch(requiredArch, CpuArch.CURRENT)
    }

    if (isIgnoreCompatibility) {
      return null
    }

    try {
      val sinceBuild = descriptor.getSinceBuild()
      if (sinceBuild != null) {
        val pluginName = descriptor.getName()
        val sinceBuildNumber = try {
          BuildNumber.fromString(sinceBuild, pluginName, null)
        }
        catch (e: RuntimeException) {
          logger.error(e)
          null
        }
        if (sinceBuildNumber != null && sinceBuildNumber > ideBuildNumber) {
          return PluginIncompatibilityReason.SinceBuildConstraintViolation(ideBuildNumber)
        }
      }

      val untilBuild = descriptor.getUntilBuild()
      if (untilBuild != null) {
        val pluginName = descriptor.getName()
        val untilBuildNumber = BuildNumber.fromString(untilBuild, pluginName, null)
        if (untilBuildNumber != null && untilBuildNumber < ideBuildNumber) {
          return PluginIncompatibilityReason.UntilBuildConstraintViolation(ideBuildNumber)
        }
      }
    }
    catch (e: Exception) {
      logger.error(e)
      return PluginIncompatibilityReason.MalformedSinceUntilConstraints
    }
    return null
  }

  private val OS_ARCH_DEPENDENCY_VERSION: Regex = Regex("([\\w.]+)-(\\w+)-(\\w+)")

  @ApiStatus.Internal
  fun getUnfulfilledOsRequirement(descriptor: IdeaPluginDescriptor): IdeaPluginOsRequirement? {
    if (descriptor.dependencies.isEmpty()) {
      // try to infer Arch requirement from version, some plugin repositories do not provide dependencies
      val matchedVersion = descriptor.version?.let { OS_ARCH_DEPENDENCY_VERSION.matchEntire(it) }
      val osTag = matchedVersion?.groupValues[2] ?: return null

      val logMessage = "Required OS for ${descriptor.pluginId} version: ${descriptor.version} is $osTag"
      logger.debug(logMessage)

      return OS.fromString(osTag)
        .takeIf { it != OS.Other }
        ?.let { IdeaPluginOsRequirement.fromOs(it) }
        ?.takeIf { osReq -> !osReq.isHostOs() }
        ?.also { logger.warn(logMessage) }
    }

    return descriptor.getDependencies().asSequence()
      .mapNotNull { dep -> IdeaPluginOsRequirement.fromModuleId(dep.pluginId).takeIf { !dep.isOptional } }
      .firstOrNull { osReq -> !osReq.isHostOs() }
  }

  @ApiStatus.Internal
  fun getUnfulfilledCpuArchRequirement(descriptor: IdeaPluginDescriptor): PluginCpuArchRequirement? {
    if (descriptor.dependencies.isEmpty()) {
      // try to infer Arch requirement from version, some plugin repositories do not provide dependencies
      val matchedVersion = descriptor.version?.let { OS_ARCH_DEPENDENCY_VERSION.matchEntire(it) }
      val archTag = matchedVersion?.groupValues[3] ?: return null

      val logMessage = "Required arch for ${descriptor.pluginId} version: ${descriptor.version} is $archTag"
      logger.debug(logMessage)

      return CpuArch.fromString(archTag)
        .takeIf { it != CpuArch.OTHER && it != CpuArch.UNKNOWN }
        ?.let { PluginCpuArchRequirement.fromArch(it) }
        ?.takeIf { osReq -> !osReq.isHostArch() }
        ?.also { logger.warn(logMessage) }
    }

    return descriptor.getDependencies().asSequence()
      .mapNotNull { dep -> PluginCpuArchRequirement.fromPluginId(dep.pluginId).takeIf { !dep.isOptional } }
      .firstOrNull { osReq -> !osReq.isHostArch() }
  }
}

@ApiStatus.Internal
sealed interface PluginIncompatibilityReason {
  class IncompatibleWithHostPlatform(val requiredOS: IdeaPluginOsRequirement, val hostOS: OS) : PluginIncompatibilityReason

  class IncompatibleWithCpuArch(val requiredArch: PluginCpuArchRequirement, val hostArch: CpuArch) : PluginIncompatibilityReason

  class SinceBuildConstraintViolation(val productBuildNumber: BuildNumber): PluginIncompatibilityReason

  class UntilBuildConstraintViolation(val productBuildNumber: BuildNumber): PluginIncompatibilityReason

  object MalformedSinceUntilConstraints : PluginIncompatibilityReason
}
