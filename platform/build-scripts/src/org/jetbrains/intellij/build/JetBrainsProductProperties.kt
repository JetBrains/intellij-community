// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.jetbrains.plugin.structure.base.plugin.PluginCreationResult
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.problems.InvalidDescriptorProblem
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Companion.Suppliers
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.function.BiPredicate

/**
 * Describes distribution of an in-house IntelliJ-based IDE hosted in IntelliJ repository.
 */
abstract class JetBrainsProductProperties : ProductProperties() {
  init {
    scrambleMainJar = true
    includeIntoSourcesArchiveFilter = BiPredicate(::isCommunityModule)
    sbomOptions.creator = "Organization: ${Suppliers.JETBRAINS}"
    sbomOptions.license = SoftwareBillOfMaterials.Options.DistributionLicense.JETBRAINS

    productLayout.addPlatformSpec { layout, _ ->
      layout.withModule(IJENT_BOOT_CLASSPATH_MODULE, PLATFORM_CORE_NIO_FS)
      xBootClassPathJarNames += PLATFORM_CORE_NIO_FS
    }
  }

  protected fun isCommunityModule(module: JpsModule, context: BuildContext): Boolean {
    return module.contentRootsList.urls.all { url ->
      Path.of(JpsPathUtil.urlToPath(url)).startsWith(context.paths.communityHomeDir)
    }
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) {
  }

  override fun validatePlugin(pluginId: String?, result: PluginCreationResult<IdePlugin>, context: BuildContext): List<PluginProblem> {
    return buildList {
      val problems = super.validatePlugin(pluginId, result, context).filterNot {
        (
          // FIXME IDEA-356970
          pluginId == "com.intellij.plugins.projectFragments" ||
          // FIXME IJPL-159498
          pluginId == "org.jetbrains.plugins.docker.gateway"
        ) && it.message.contains("Service preloading is deprecated") ||
        (
          // FIXME PY-74322
          pluginId == "com.intellij.python.frontend" ||
          // FIXME AE-121
          pluginId == "com.jetbrains.personalization"
        ) && it.message.contains("Plugin has no dependencies")
      }
      addAll(problems)
      if (result is PluginCreationSuccess && result.plugin.vendor?.contains("JetBrains") != true) {
        add(object : InvalidDescriptorProblem(
          descriptorPath = "",
          detailedMessage = "${result.plugin.pluginId} is published not by JetBrains: ${result.plugin.vendor}"
        ) {
          override val level = Level.ERROR
        })
      }
    }
  }
}
