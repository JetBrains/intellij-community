// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

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
    @JvmField
    val EP_NAME: ExtensionPointName<DependencyCollectorBean> = ExtensionPointName("com.intellij.dependencyCollector")
  }

  override fun getImplementationClassName(): String = implementation
}

/**
 * Collects dependencies for the given project, so that the IDE can offer to enable/install plugins supporting those dependencies.
 * Implementations of this interface are registered through the `dependencyCollector` extension point.
 *
 * The plugins which need to be suggested must define "dependencySupport"
 * with a coordinate that corresponds to one of the dependencies with the same "kind".
 */
interface DependencyCollector {
  /**
   * Returns the list of dependencies for the given project. Each element in the returned list is the name/coordinate of a dependency.
   * See [DependencyInformation] for more details on how to describe a dependency.
   */
  suspend fun collectDependencies(project: Project): Collection<DependencyInformation>
}

/**
 * Describes existing dependencies of the give project or the user environment.
 * The [coordinate] is a name or the coordinate of the dependency. The specific format depends on the dependency kind.
 *   For Java, the format is Maven group ID and artifact ID separated by a colon.
 *
 * The [reason] is the user-visible reason why the plugin is suggested to the user.
 *   The rule is to use "This plugin is suggested because XYZ" format, where XYZ is the reason why the plugin is suggested.
 *   For example, "This plugin is suggested because you have docker installed".
 *   If null, the reason defaults to "This plugin is suggested because your project uses [coordinate] library"
 */
data class DependencyInformation(
  val coordinate: String,
  @param:Nls val reason: String? = IdeBundle.message("plugins.configurable.suggested.features.dependency", coordinate),
)

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

  @ApiStatus.Experimental
  internal constructor(attributes: Map<String, String>) : this() {
    kind = attributes["kind"]!!
    coordinate = attributes["coordinate"]!!
    displayName = attributes.getOrDefault("displayName", "")
  }

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    if (pluginDescriptor is IdeaPluginDescriptorImpl && pluginDescriptor !is ContentModuleDescriptor) {
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

  override fun toString(): String {
    return "DependencySupportBean(coordinate='$coordinate', kind='$kind')"
  }
}

internal const val DEPENDENCY_SUPPORT_FEATURE: String = "dependencySupport"
internal const val FILE_HANDLER_KIND: String = "file-handler"

internal val DependencySupportBean.id: @NlsSafe String
  get() = "$kind:$coordinate"

private val DEPENDENCY_COLLECTOR_EP_NAME: ExtensionPointName<DependencySupportBean> =
  ExtensionPointName("com.intellij.dependencySupport")

internal val DependencySupportBean.displayNameOrId: @NlsSafe String
  get() = displayName.ifEmpty { id }

private class DependencyFeatureCollector : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<PluginFeatureService>().collectFeatureMapping(
      featureType = DEPENDENCY_SUPPORT_FEATURE,
      ep = DEPENDENCY_COLLECTOR_EP_NAME,
      idMapping = { it.id },
      displayNameMapping = { it.displayNameOrId },
    )
  }
}
