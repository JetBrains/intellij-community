// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.annotations.Attribute

interface DependencyCollector {
  val dependencyKind: String

  fun collectDependencies(project: Project): List<String>

  companion object {
    val EP_NAME = ExtensionPointName.create<DependencyCollector>("com.intellij.dependencyCollector")
  }
}

class DependencySupportBean : PluginAware {
  private var pluginDescriptor: PluginDescriptor? = null

  @Attribute("kind")
  @JvmField
  @RequiredElement
  var kind: String = ""

  @Attribute("coordinate")
  @JvmField
  @RequiredElement
  var coordinate: String = ""

  @Attribute("displayName")
  @JvmField
  @NlsSafe
  var displayName: String = ""

  companion object {
    val EP_NAME = ExtensionPointName.create<DependencySupportBean>("com.intellij.dependencySupport")
  }

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }
}

const val DEPENDENCY_SUPPORT_FEATURE = "dependencySupport"

class DependencyFeatureCollector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      DEPENDENCY_SUPPORT_FEATURE,
      DependencySupportBean.EP_NAME,
      { bean -> bean.kind + ":" + bean.coordinate },
      { bean -> bean.displayName.ifEmpty { bean.kind + ":" + bean.coordinate } }
    )
  }
}
