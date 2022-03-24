// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @JvmField internal var `package`: String? = null

  @JvmField internal var url: String? = null
  @JvmField internal var vendor: String? = null
  @JvmField internal var vendorEmail: String? = null
  @JvmField internal var vendorUrl: String? = null

  @JvmField internal var resourceBundleBaseName: String? = null

  @JvmField internal var isUseIdeaClassLoader = false
  @JvmField internal var isBundledUpdateAllowed = false
  @JvmField internal var implementationDetail = false
  @JvmField internal var isRestartRequired = false
  @JvmField internal var isLicenseOptional = false

  @JvmField internal var productCode: String? = null
  @JvmField internal var releaseDate: LocalDate? = null
  @JvmField internal var releaseVersion = 0

  @JvmField internal var modules: MutableList<PluginId>? = null

  @JvmField internal var depends: MutableList<PluginDependency>? = null
  @JvmField internal var actions: MutableList<ActionDescriptor>? = null

  @JvmField var incompatibilities: MutableList<PluginId>? = null

  @JvmField val appContainerDescriptor = ContainerDescriptor()
  @JvmField val projectContainerDescriptor = ContainerDescriptor()
  @JvmField val moduleContainerDescriptor = ContainerDescriptor()

  @JvmField var epNameToExtensions: MutableMap<String, MutableList<ExtensionDescriptor>>? = null

  @JvmField internal var contentModules: MutableList<PluginContentDescriptor.ModuleItem>? = null
  @JvmField internal var dependencies = ModuleDependenciesDescriptor.EMPTY

  class ActionDescriptor(
    @JvmField val name: String,
    @JvmField val element: XmlElement,
    @JvmField val resourceBundle: String?,
  )
}
