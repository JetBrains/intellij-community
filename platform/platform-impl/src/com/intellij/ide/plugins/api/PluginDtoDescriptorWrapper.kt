// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginDependency
import com.intellij.ide.plugins.PluginDependencyImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.*

@IntellijInternalApi
internal class PluginDtoDescriptorWrapper(private val pluginDto: PluginDto) : IdeaPluginDescriptor {

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
    return pluginDto.displayCategory
  }

  override fun getSinceBuild(): String? = pluginDto.sinceBuild

  override fun getUntilBuild(): String? = pluginDto.untilBuild

  override fun getDescription(): @Nls String? = pluginDto.description

  override fun getVendor(): String? = pluginDto.vendor

  override fun getVendorEmail(): String? {
    LOG.error("No direct mapping for vendorEmail in PluginDto")
    return null
  }

  override fun getVendorUrl(): String? {
    LOG.error("No direct mapping for vendorUrl in PluginDto")
    return null
  }

  override fun getUrl(): String? {
    LOG.error("No direct mapping for url in PluginDto")
    return null
  }

  override fun isBundled(): Boolean = pluginDto.isBundled

  override fun allowBundledUpdate(): Boolean = pluginDto.allowBundledUpdate

  override fun isImplementationDetail(): Boolean = pluginDto.isImplementationDetail

  override fun isRequireRestart(): Boolean {
    LOG.error("No direct mapping for isRequireRestart in PluginDto")
    return false
  }

  override fun getDependencies(): List<PluginDependency> = dependenciesList

  override fun getResourceBundleBaseName(): String? {
    LOG.error("No direct mapping for resourceBundleBaseName in PluginDto")
    return null
  }

  override fun getPluginPath(): Path? {
    return null
  }

  override fun getDescriptorPath(): String? {
    LOG.error("No direct mapping for descriptorPath in PluginDto")
    return null
  }

  override fun getPluginClassLoader(): ClassLoader? {
    LOG.error("No direct mapping for pluginClassLoader in PluginDto")
    return null
  }

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean = pluginDto.isEnabled

  @Deprecated("see com.intellij.openapi.extensions.PluginDescriptor.setEnabled")
  override fun setEnabled(enabled: Boolean) {
    LOG.error("Write operations are not allowed here")
  }

  companion object {
    private val LOG = Logger.getInstance(PluginDtoDescriptorWrapper::class.java)
  }
}