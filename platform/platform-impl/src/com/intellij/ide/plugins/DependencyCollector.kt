// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute

class DependencyCollectorBean : BaseKeyedLazyInstance<DependencyCollector>() {
  @Attribute("kind")
  @JvmField
  @RequiredElement
  var kind: String = ""

  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementation: String = ""

  companion object {
    val EP_NAME = ExtensionPointName.create<DependencyCollectorBean>("com.intellij.dependencyCollector")
  }

  override fun getImplementationClassName(): String = implementation
}

/**
 * Collects dependencies for the given project, so that the IDE can offer to enable/install plugins supporting those dependencies.
 * Implementations of this interface are registered through the `dependencyCollector` extension point.
 */
interface DependencyCollector {
  /**
   * Returns the list of dependencies for the given project. Each element in the returned list is the name/coordinate of a dependency.
   * The specific format of returned strings depends on the dependency kind. For Java, the format is Maven group ID and artifact ID
   * separated by a colon.
   */
  fun collectDependencies(project: Project): List<String>
}

/**
 * Marks a plugin as supporting a given dependency. The `coordinate` attribute specifies the name or coordinate of the supported
 * library/dependency, in the same format as returned from [DependencyCollector.collectDependencies] for the respective dependency kind.
 */
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

  /**
   * The user-readable name of the corresponding library or framework. Shown to the user in messages suggesting to install/enable plugins.
   */
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
