// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xml.dom.XmlElement
import org.jetbrains.annotations.ApiStatus
import java.time.LocalDate

@ApiStatus.Internal
class RawPluginDescriptor {
  @JvmField var id: String? = null
  @JvmField internal var name: String? = null
  @JvmField internal var description: @NlsSafe String? = null
  @JvmField internal var category: String? = null
  @JvmField internal var changeNotes: String? = null

  @JvmField internal var version: String? = null
  @JvmField internal var sinceBuild: String? = null
  @JvmField internal var untilBuild: String? = null

  @JvmField var `package`: String? = null
  @JvmField var isSeparateJar: Boolean = false

  @JvmField internal var url: String? = null
  @JvmField internal var vendor: String? = null
  @JvmField internal var vendorEmail: String? = null
  @JvmField internal var vendorUrl: String? = null

  @JvmField internal var resourceBundleBaseName: String? = null

  @JvmField internal var isUseIdeaClassLoader: Boolean = false
  @JvmField internal var isBundledUpdateAllowed: Boolean = false
  @JvmField internal var implementationDetail: Boolean = false
  @JvmField internal var isRestartRequired: Boolean = false
  @JvmField internal var isLicenseOptional: Boolean = false
  // makes sense only for product modules for now
  @JvmField internal var isDependentOnCoreClassLoader: Boolean = true

  @JvmField internal var productCode: String? = null
  @JvmField internal var releaseDate: LocalDate? = null
  @JvmField internal var releaseVersion: Int = 0

  @JvmField internal var pluginAliases: MutableList<PluginId>? = null

  @JvmField internal var depends: MutableList<PluginDependency>? = null
  @JvmField internal var actions: MutableList<ActionDescriptor>? = null

  @JvmField var incompatibilities: MutableList<PluginId>? = null

  @JvmField val appContainerDescriptor: ContainerDescriptor = ContainerDescriptor()
  @JvmField val projectContainerDescriptor: ContainerDescriptor = ContainerDescriptor()
  @JvmField val moduleContainerDescriptor: ContainerDescriptor = ContainerDescriptor()

  @JvmField var epNameToExtensions: MutableMap<String, MutableList<ExtensionDescriptor>>? = null

  @JvmField internal var contentModules: MutableList<PluginContentDescriptor.ModuleItem>? = null
  @JvmField internal var dependencies: ModuleDependenciesDescriptor = ModuleDependenciesDescriptor.EMPTY

  sealed class ActionDescriptor(
    @JvmField val name: ActionDescriptorName,
    @JvmField val element: XmlElement,
    @JvmField val resourceBundle: String?,
  )

  class ActionDescriptorMisc(
    name: ActionDescriptorName,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionDescriptor(name, element, resourceBundle)

  class ActionDescriptorAction(
    @JvmField val className: String,
    @JvmField val isInternal: Boolean,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionDescriptor(name = ActionDescriptorName.action, element = element, resourceBundle = resourceBundle)

  class ActionDescriptorGroup(
    @JvmField val className: String?,
    @JvmField val id: String?,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionDescriptor(name = ActionDescriptorName.group, element = element, resourceBundle = resourceBundle)
}

@ApiStatus.Internal
@Suppress("EnumEntryName")
enum class ActionDescriptorName {
  action, group, separator, reference, unregister, prohibit,
}
