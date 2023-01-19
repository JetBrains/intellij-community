// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

internal class DependencyCollectorBean : BaseKeyedLazyInstance<DependencyCollector>() {

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
 *
 * The plugins which need to be suggested must define "dependencySupport"
 * with a coordinate that corresponding to one of the dependencies with the same "kind".
 */
interface DependencyCollector {
  /**
   * Returns the list of dependencies for the given project. Each element in the returned list is the name/coordinate of a dependency.
   * The specific format of returned strings depends on the dependency kind. For Java, the format is Maven group ID and artifact ID
   * separated by a colon.
   */
  fun collectDependencies(project: Project): Collection<String>
}

/**
 * Marks a plugin as supporting a given dependency. The `coordinate` attribute specifies the name or coordinate of the supported
 * library/dependency, in the same format as returned from [DependencyCollector.collectDependencies] for the respective dependency kind.
 */
internal class DependencySupportBean() : PluginAware {

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

  @ApiStatus.Experimental
  internal constructor(attributes: Map<String, String>) : this() {
    kind = attributes["kind"]!!
    coordinate = attributes["coordinate"]!!
    displayName = attributes.getOrDefault("displayName", "")
  }

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    if (pluginDescriptor is IdeaPluginDescriptorImpl && pluginDescriptor.moduleName == null) {
      this.pluginDescriptor = pluginDescriptor
    }
    else {
      val descriptorPath = (pluginDescriptor as? IdeaPluginDescriptor)?.descriptorPath
      throw PluginException(
        "$DEPENDENCY_SUPPORT_FEATURE should be registered only in a `${PluginManagerCore.PLUGIN_XML_PATH}` file, actual descriptor path: `$descriptorPath`",
        pluginDescriptor.pluginId,
      )
    }
  }
}

internal const val DEPENDENCY_SUPPORT_FEATURE = "dependencySupport"

internal val DependencySupportBean.id: @NlsSafe String
  get() = "$kind:$coordinate"

internal val DependencySupportBean.displayNameOrId: @NlsSafe String
  get() = displayName.ifEmpty { id }

internal class DependencyFeatureCollector : ProjectPostStartupActivity {

  override suspend fun execute(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      DEPENDENCY_SUPPORT_FEATURE,
      DependencySupportBean.EP_NAME,
      { it.id },
      { it.displayNameOrId },
    )
  }
}
