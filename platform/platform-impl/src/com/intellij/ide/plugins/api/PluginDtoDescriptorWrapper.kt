// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.ContentModule
import com.intellij.ide.plugins.IdeaPluginDescriptorEx
import com.intellij.ide.plugins.ModuleDependencies
import com.intellij.ide.plugins.ModuleLoadingRule
import com.intellij.ide.plugins.PluginDependencyImpl
import com.intellij.ide.plugins.PluginDependency
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.elements.ActionElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.Date

@ApiStatus.Internal
class PluginDtoDescriptorWrapper(private val pluginDto: PluginDto) : IdeaPluginDescriptorEx {

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

  override fun getSinceBuild(): String? {
    throw UnsupportedOperationException("No direct mapping for sinceBuild in PluginDto")
  }

  override fun getUntilBuild(): String? {
    throw UnsupportedOperationException("No direct mapping for untilBuild in PluginDto")
  }

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

  override val moduleName: String?
    get() = throw UnsupportedOperationException("No direct mapping for moduleName in PluginDto")

  override val moduleLoadingRule: ModuleLoadingRule
    get() = throw UnsupportedOperationException("No direct mapping for moduleLoadingRule in PluginDto")

  override val incompatiblePlugins: List<PluginId>
    get() = throw UnsupportedOperationException("No direct mapping for incompatiblePlugins in PluginDto")

  override val pluginAliases: List<PluginId>
    get() = throw UnsupportedOperationException("No direct mapping for pluginAliases in PluginDto")

  override val moduleDependencies: ModuleDependencies
    get() = throw UnsupportedOperationException("No direct mapping for moduleDependencies in PluginDto")

  override val packagePrefix: String?
    get() = throw UnsupportedOperationException("No direct mapping for packagePrefix in PluginDto")

  override val contentModules: List<ContentModule>
    get() = throw UnsupportedOperationException("No direct mapping for contentModules in PluginDto")

  override val appContainerDescriptor: ContainerDescriptor
    get() = throw UnsupportedOperationException("No direct mapping for appContainerDescriptor in PluginDto")

  override val projectContainerDescriptor: ContainerDescriptor
    get() = throw UnsupportedOperationException("No direct mapping for projectContainerDescriptor in PluginDto")

  override val moduleContainerDescriptor: ContainerDescriptor
    get() = throw UnsupportedOperationException("No direct mapping for moduleContainerDescriptor in PluginDto")

  override val extensions: Map<String, List<ExtensionDescriptor>>
    get() = throw UnsupportedOperationException("No direct mapping for extensions in PluginDto")

  override val actions: List<ActionElement>
    get() = throw UnsupportedOperationException("No direct mapping for actions in PluginDto")

  override val isUseIdeaClassLoader: Boolean
    get() = throw UnsupportedOperationException("No direct mapping for isUseIdeaClassLoader in PluginDto")

  override val isIndependentFromCoreClassLoader: Boolean
    get() = throw UnsupportedOperationException("No direct mapping for isIndependentFromCoreClassLoader in PluginDto")

  override val useCoreClassLoader: Boolean
    get() = throw UnsupportedOperationException("No direct mapping for useCoreClassLoader in PluginDto")

  override var isMarkedForLoading: Boolean = pluginDto.isEnabled

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean = pluginDto.isEnabled

  @Deprecated("Deprecated in Java")
  override fun setEnabled(enabled: Boolean) {
    // Cannot modify the PluginDto directly here
    isMarkedForLoading = enabled
  }

  override fun toString(): String =
    "PluginDtoDescriptorWrapper(name=${getName()}, id=${getPluginId()}, version=${getVersion()}, isBundled=${isBundled()})"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IdeaPluginDescriptorEx) return false
    return getPluginId() == other.pluginId
  }

  override fun hashCode(): Int = getPluginId().hashCode()
}