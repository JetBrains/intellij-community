// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginDependencyImpl
import com.intellij.ide.plugins.PluginDependency
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.Date

@ApiStatus.Internal
class PluginDtoDescriptorWrapper(private val pluginDto: PluginDto) : IdeaPluginDescriptor {

  private val dependenciesList: List<PluginDependency> by lazy {
    pluginDto.dependencies.map {
      PluginDependencyImpl(it.pluginId, null, it.isOptional)
    }
  }

  override fun getPluginId(): PluginId = pluginDto.pluginId

  override fun getName(): String = pluginDto.name ?: pluginDto.pluginId.idString

  override fun getVersion(): String? = pluginDto.version

  override fun getProductCode(): String? = pluginDto.productCode

  override fun getReleaseDate(): Date? = if (pluginDto.date > 0) Date(pluginDto.date) else null

  override fun getReleaseVersion(): Int = pluginDto.releaseVersion

  override fun isLicenseOptional(): Boolean = pluginDto.isLicenseOptional

  override fun getChangeNotes(): String? = pluginDto.changeNotes

  override fun getCategory(): @NlsSafe String? = pluginDto.category

  override fun getDisplayCategory(): @Nls String? {
    throw UnsupportedOperationException("No direct mapping for displayCategory in PluginDto")
  }

  override fun getSinceBuild(): String? = pluginDto.sinceBuild

  override fun getUntilBuild(): String? = pluginDto.untilBuild

  override fun getDescription(): @Nls String? = pluginDto.description

  override fun getVendor(): String? = pluginDto.vendor

  override fun getVendorEmail(): String? {
    throw UnsupportedOperationException("No direct mapping for vendorEmail in PluginDto")
  }

  override fun getVendorUrl(): String? {
    throw UnsupportedOperationException("No direct mapping for vendorUrl in PluginDto")
  }

  override fun getUrl(): String? {
    throw UnsupportedOperationException("No direct mapping for url in PluginDto")
  }

  override fun isBundled(): Boolean = pluginDto.isBundled

  override fun allowBundledUpdate(): Boolean = pluginDto.allowBundledUpdate

  override fun isImplementationDetail(): Boolean {
    throw UnsupportedOperationException("No direct mapping for isImplementationDetail in PluginDto")
  }

  override fun isRequireRestart(): Boolean {
    throw UnsupportedOperationException("No direct mapping for isRequireRestart in PluginDto")
  }

  override fun getDependencies(): List<PluginDependency> = dependenciesList

  override fun getResourceBundleBaseName(): String? {
    throw UnsupportedOperationException("No direct mapping for resourceBundleBaseName in PluginDto")
  }

  override fun getPluginPath(): Path? {
    return null
  }

  override fun getDescriptorPath(): String? {
    throw UnsupportedOperationException("No direct mapping for descriptorPath in PluginDto")
  }

  override fun getPluginClassLoader(): ClassLoader? {
    throw UnsupportedOperationException("No direct mapping for pluginClassLoader in PluginDto")
  }

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean = pluginDto.isEnabled

  override fun setEnabled(enabled: Boolean) {
    throw UnsupportedOperationException("Write operations are not allowed here")
  }

}