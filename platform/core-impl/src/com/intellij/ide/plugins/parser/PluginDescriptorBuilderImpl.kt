// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

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
}