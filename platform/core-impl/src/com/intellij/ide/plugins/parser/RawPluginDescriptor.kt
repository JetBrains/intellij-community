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

  @JvmField var url: String? = null
  @JvmField var vendor: String? = null
  @JvmField var vendorEmail: String? = null
  @JvmField var vendorUrl: String? = null

  @JvmField var resourceBundleBaseName: String? = null

  @JvmField var isUseIdeaClassLoader: Boolean = false
  @JvmField var isBundledUpdateAllowed: Boolean = false
  @JvmField var implementationDetail: Boolean = false
  @JvmField var isRestartRequired: Boolean = false
  @JvmField var isLicenseOptional: Boolean = false
  // makes sense only for product modules for now
  @JvmField var isIndependentFromCoreClassLoader: Boolean = false

  @JvmField var productCode: String? = null
  @JvmField var releaseDate: LocalDate? = null
  @JvmField var releaseVersion: Int = 0

  @JvmField var pluginAliases: MutableList<String>? = null

  @JvmField var depends: MutableList<DependsElement>? = null
  @JvmField var actions: MutableList<ActionElement>? = null

  @JvmField var incompatibleWith: MutableList<String>? = null

  @JvmField val appContainerDescriptor: ScopedElementsContainerBuilder = ScopedElementsContainerBuilderMemoryOptimized()
  @JvmField val projectContainerDescriptor: ScopedElementsContainerBuilder = ScopedElementsContainerBuilderMemoryOptimized()
  @JvmField val moduleContainerDescriptor: ScopedElementsContainerBuilder = ScopedElementsContainerBuilderMemoryOptimized()

  /** key is extension point's FQN */
  @JvmField var miscExtensions: MutableMap<String, MutableList<MiscExtensionElement>>? = null

  @JvmField var contentModules: MutableList<ContentElement>? = null
  @JvmField var dependencies: MutableList<DependenciesElement>? = null
}


