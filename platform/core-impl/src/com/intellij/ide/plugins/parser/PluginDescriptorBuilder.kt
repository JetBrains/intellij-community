// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

interface PluginDescriptorBuilder {
  var id: String?
  var name: String?

  var description: String?
  var category: String?
  var changeNotes: String?

  var version: String?
  var sinceBuild: String?
  @Deprecated("Deprecated since 2025.2, the value is disregarded if its major part is at least 251. " +
              "Nonetheless, IDE consults since-until constraints taken directly from the Marketplace, so they can be set there if you need it.")
  var untilBuild: String?

  var `package`: String?
  var isSeparateJar: Boolean

  var url: String?
  var vendor: String?
  var vendorEmail: String?
  var vendorUrl: String?
}