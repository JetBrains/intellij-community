// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.parser.elements.ActionElement
import com.intellij.ide.plugins.parser.elements.DependsElement
import com.intellij.util.Java11Shim
import java.time.LocalDate

internal class PluginDescriptorBuilderImpl : PluginDescriptorBuilder {
  override var id: String? = null
  override var name: String? = null

  override var description: String? = null
  override var category: String? = null
  override var changeNotes: String? = null

  override var version: String? = null
  override var sinceBuild: String? = null
  @Deprecated("Deprecated since 2025.2, the value is disregarded if its major part is at least 251. " +
              "Nonetheless, IDE consults since-until constraints taken directly from the Marketplace, so they can be set there if you need it.")
  override var untilBuild: String? = null

  override var `package`: String? = null
  override var isSeparateJar: Boolean = false

  override var url: String? = null
  override var vendor: String? = null
  override var vendorEmail: String? = null
  override var vendorUrl: String? = null

  override var resourceBundleBaseName: String? = null

  override var isUseIdeaClassLoader: Boolean = false
  override var isBundledUpdateAllowed: Boolean = false
  override var implementationDetail: Boolean = false
  override var isRestartRequired: Boolean = false
  override var isLicenseOptional: Boolean = false
  override var isIndependentFromCoreClassLoader: Boolean = false

  override var productCode: String? = null
  override var releaseDate: LocalDate? = null
  override var releaseVersion: Int = 0

  private var _pluginAliases: MutableList<String>? = null
  override fun addPluginAlias(alias: String) {
    if (_pluginAliases == null) {
      _pluginAliases = ArrayList()
    }
    _pluginAliases!!.add(alias)
  }

  override val pluginAliases: List<String>
    get() = _pluginAliases ?: Java11Shim.INSTANCE.listOf()

  private var _depends: MutableList<DependsElement>? = null
  override fun addDepends(depends: DependsElement) {
    if (_depends == null) {
      _depends = ArrayList()
    }
    _depends!!.add(depends)
  }

  override val depends: List<DependsElement>
    get() = _depends ?: Java11Shim.INSTANCE.listOf()

  private var _actions: MutableList<ActionElement>? = null
  override fun addAction(action: ActionElement) {
    if (_actions == null) {
      _actions = ArrayList()
    }
    _actions!!.add(action)
  }
  override val actions: List<ActionElement>
    get() = _actions ?: Java11Shim.INSTANCE.listOf()

  private var _incompatibleWith: MutableList<String>? = null
  override fun addIncompatibleWith(incompatibleWith: String) {
    if (_incompatibleWith == null) {
      _incompatibleWith = ArrayList()
    }
    _incompatibleWith!!.add(incompatibleWith)
  }
  override val incompatibleWith: List<String>
    get() = _incompatibleWith ?: Java11Shim.INSTANCE.listOf()

  override val appContainerBuilder: ScopedElementsContainerBuilder = ScopedElementsContainerBuilderMemoryOptimized()
  override val projectContainerBuilder: ScopedElementsContainerBuilder = ScopedElementsContainerBuilderMemoryOptimized()
  override val moduleContainerBuilder: ScopedElementsContainerBuilder = ScopedElementsContainerBuilderMemoryOptimized()
}