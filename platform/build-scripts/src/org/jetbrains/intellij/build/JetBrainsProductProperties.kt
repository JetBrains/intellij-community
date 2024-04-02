// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.jetbrains.plugin.structure.base.plugin.PluginCreationResult
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.problems.InvalidDescriptorProblem
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Companion.Suppliers
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
    sbomOptions.copyrightText = "Copyright 2000-2023 ${Suppliers.JETBRAINS} and contributors"
    sbomOptions.license = SoftwareBillOfMaterials.Options.DistributionLicense.JETBRAINS
  }

  protected fun isCommunityModule(module: JpsModule, context: BuildContext): Boolean {
    return module.contentRootsList.urls.all { url ->
      Path.of(JpsPathUtil.urlToPath(url)).startsWith(context.paths.communityHomeDir)
    }
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) {
    copyAdditionalFilesBlocking(context, targetDir)
  }

  @Obsolete
  protected open fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: Path) {
  }

  override fun validatePlugin(result: PluginCreationResult<IdePlugin>, context: BuildContext): List<PluginProblem> {
    return buildList {
      addAll(super.validatePlugin(result, context))
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
