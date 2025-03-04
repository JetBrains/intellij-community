// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.parser.elements.*
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.time.LocalDate

@ApiStatus.Internal
class RawPluginDescriptor {
  val builder: PluginDescriptorBuilder = PluginDescriptorBuilderImpl()

  val id: String? get() = builder.id
  val name: String? get() = builder.name
  val description: @NlsSafe String? get() = builder.description
  val category: String? get() = builder.category
  val changeNotes: String? get() = builder.changeNotes

  val version: String? get() = builder.version
  val sinceBuild: String? get() = builder.sinceBuild
  @Deprecated("Deprecated since 2025.2, the value is disregarded if its major part is at least 251. " +
              "Nonetheless, IDE consults since-until constraints taken directly from the Marketplace, so they can be set there if you need it.")
  val untilBuild: String? get() = builder.untilBuild

  val `package`: String? get() = builder.`package`
  val isSeparateJar: Boolean get() = builder.isSeparateJar

  val url: String? get() = builder.url
  val vendor: String? get() = builder.vendor
  val vendorEmail: String? get() = builder.vendorEmail
  val vendorUrl: String? get() = builder.vendorUrl

  val resourceBundleBaseName: String? get() = builder.resourceBundleBaseName

  val isUseIdeaClassLoader: Boolean get() = builder.isUseIdeaClassLoader
  val isBundledUpdateAllowed: Boolean get() = builder.isBundledUpdateAllowed
  val implementationDetail: Boolean get() = builder.implementationDetail
  val isRestartRequired: Boolean get() = builder.isRestartRequired
  val isLicenseOptional: Boolean get() = builder.isLicenseOptional
  // makes sense only for product modules for now
  val isIndependentFromCoreClassLoader: Boolean get() = builder.isIndependentFromCoreClassLoader

  val productCode: String? get() = builder.productCode
  val releaseDate: LocalDate? get() = builder.releaseDate
  val releaseVersion: Int get() = builder.releaseVersion

  val pluginAliases: List<String> get() = builder.pluginAliases

  val depends: List<DependsElement> get() = builder.depends
  val actions: List<ActionElement> get() = builder.actions

  val incompatibleWith: List<String> get() = builder.incompatibleWith

  val appElementsContainer: ScopedElementsContainer get() = builder.appContainerBuilder.build()
  val projectElementsContainer: ScopedElementsContainer get() = builder.projectContainerBuilder.build()
  val moduleElementsContainer: ScopedElementsContainer get() = builder.moduleContainerBuilder.build()

  /** key is extension point's FQN */
  val miscExtensions: Map<String, List<MiscExtensionElement>> get() = builder.miscExtensions

  val contentModules: List<ContentElement> get() = builder.contentModules
  val dependencies: List<DependenciesElement> get() = builder.dependencies
}


