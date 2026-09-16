// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// `PluginContentReport` holds four fields, so a positional destructuring would hide which field the loop reads.
@file:Suppress("DestructuringDeclaration")

package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingCheckFailure
import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetValidationContext
import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetValidationSpec
import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetValidationStage
import com.intellij.platform.buildScripts.testFramework.distributionContent.ParsedContentReport
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.model.module.JpsModule

/**
 * Checks that the layout the build computes states the same plugin classpath as the packaged distribution.
 *
 * The plugin-dependency validation reads the layout, so it runs beside the packaging instead of after it. That is only
 * sound while the two answers agree, and this validation is what holds them together. It reads the packaged content
 * report, so it runs after the packaging of its target.
 *
 * The model may name a content module of the plugin that the report puts under `contentModules`. An `auto` layout
 * infers a child from a direct dependency of the main module, and it reads no plugin descriptor, so it cannot tell a
 * content module from an ordinary dependency. `PluginDependenciesValidator` reads the descriptor and settles the
 * question on its own, so such a name is not a difference and this check drops it.
 *
 * The library roots of the model are wider than the packed set on purpose, so the check is one-sided: a root the
 * report has must be in the model, and a root the model has alone is fine.
 */
@Internal
fun createPluginLayoutParityValidationSpec(config: PluginModuleDependencyValidationConfig): PackagingTargetValidationSpec {
  return PackagingTargetValidationSpec(
    targetId = config.targetId,
    name = PLUGIN_LAYOUT_PARITY_VALIDATION_NAME,
    problemMessage = "plugin layout parity problems",
    stage = PackagingTargetValidationStage.CONTENT,
    validator = { context -> validatePluginLayoutParity(context = context, config = config) },
  )
}

private fun validatePluginLayoutParity(
  context: PackagingTargetValidationContext,
  config: PluginModuleDependencyValidationConfig,
): List<PackagingCheckFailure> {
  val content = context.content()
  val reportProvider = createPluginLayoutProviderByContentReport(
    context = context,
    mainModuleOfCorePlugin = config.mainModuleOfCorePlugin,
    corePluginDescriptorPath = config.corePluginDescriptorPath,
  )
  val modelProvider = createPluginLayoutProviderByDistributionState(
    context = context,
    mainModuleOfCorePlugin = config.mainModuleOfCorePlugin,
    corePluginDescriptorPath = config.corePluginDescriptorPath,
  )

  val failures = ArrayList<PackagingCheckFailure>()
  comparePluginLayout(
    pluginMainModule = config.mainModuleOfCorePlugin,
    reportLayout = reportProvider.loadCorePluginLayout(),
    modelLayout = modelProvider.loadCorePluginLayout(),
    contentModulesOfPlugin = emptySet(),
  )?.let(failures::add)

  val contentModulesByMainModule = collectContentModulesByMainModule(content)
  for (mainModuleName in contentModulesByMainModule.keys) {
    if (mainModuleName == config.mainModuleOfCorePlugin) {
      continue
    }

    val module = context.project.findModuleByName(mainModuleName) ?: continue
    val reportLayout = loadOrDescribeFailure(reportProvider, module)
    val modelLayout = loadOrDescribeFailure(modelProvider, module)
    if (reportLayout.error != null || modelLayout.error != null) {
      describeLoadDifference(mainModuleName, reportLayout, modelLayout)?.let(failures::add)
      continue
    }

    if (reportLayout.value == null && modelLayout.value == null) {
      continue
    }
    if (reportLayout.value == null || modelLayout.value == null) {
      failures.add(parityFailure(
        pluginMainModule = mainModuleName,
        message = if (modelLayout.value == null) {
          "The packaged content report states a layout for the plugin, and the build layout states none."
        }
        else {
          "The build layout states a layout for the plugin, and the packaged content report states none."
        },
      ))
      continue
    }

    comparePluginLayout(
      pluginMainModule = mainModuleName,
      reportLayout = reportLayout.value,
      modelLayout = modelLayout.value,
      contentModulesOfPlugin = contentModulesByMainModule.getValue(mainModuleName),
    )?.let(failures::add)
  }

  failures.sortBy { it.name }
  return failures
}

private class LoadResult(@JvmField val value: PluginLayoutDescription?, @JvmField val error: Throwable?)

private fun loadOrDescribeFailure(provider: PluginLayoutProvider, module: JpsModule): LoadResult {
  return try {
    LoadResult(value = provider.loadPluginLayout(module), error = null)
  }
  catch (e: Throwable) {
    LoadResult(value = null, error = e)
  }
}

/** A failure when one provider rejects the plugin and the other one does not. Both rejecting it is no difference. */
private fun describeLoadDifference(pluginMainModule: String, reportLayout: LoadResult, modelLayout: LoadResult): PackagingCheckFailure? {
  if (reportLayout.error != null && modelLayout.error != null) {
    return null
  }

  val failed = if (reportLayout.error != null) "packaged content report" else "build layout"
  val error = reportLayout.error ?: modelLayout.error
  return parityFailure(
    pluginMainModule = pluginMainModule,
    message = "The $failed rejects the plugin, and the other source accepts it: ${error?.message}",
  )
}

private fun comparePluginLayout(
  pluginMainModule: String,
  reportLayout: PluginLayoutDescription,
  modelLayout: PluginLayoutDescription,
  contentModulesOfPlugin: Set<String>,
): PackagingCheckFailure? {
  val missingInModel = reportLayout.jpsModulesInClasspath - modelLayout.jpsModulesInClasspath
  val extraInModel = modelLayout.jpsModulesInClasspath.filterTo(LinkedHashSet()) {
    it !in reportLayout.jpsModulesInClasspath && it !in contentModulesOfPlugin
  }
  val missingRoots = reportLayout.libraryRootsInClasspath.toSet() - modelLayout.libraryRootsInClasspath.toSet()
  if (missingInModel.isEmpty() && extraInModel.isEmpty() && missingRoots.isEmpty()) {
    return null
  }

  val message = buildString {
    appendLine("The build layout and the packaged content report state different classpaths for '$pluginMainModule'.")
    appendLine()
    appendNames("Modules the report has and the model does not", missingInModel)
    appendNames("Modules the model has and the report does not", extraInModel)
    appendNames("Library roots the report has and the model does not", missingRoots.map { it.toString() })
    appendNames("The modules of the report", reportLayout.jpsModulesInClasspath)
    appendNames("The modules of the model", modelLayout.jpsModulesInClasspath)
    appendLine(
      "The plugin-dependency validation reads the build layout, so a difference here means it checks a classpath the " +
      "product does not ship. Fix the rule in 'DistributionStatePluginLayoutProvider', and do not widen the module set " +
      "to make this test pass."
    )
  }
  return parityFailure(pluginMainModule = pluginMainModule, message = message)
}

private fun StringBuilder.appendNames(title: String, names: Collection<String>) {
  if (names.isEmpty()) {
    return
  }

  appendLine("$title (${names.size}):")
  for (name in names.sorted()) {
    appendLine("  $name")
  }
  appendLine()
}

private fun parityFailure(pluginMainModule: String, message: String): PackagingCheckFailure {
  return PackagingCheckFailure(name = "$PLUGIN_LAYOUT_PARITY_VALIDATION_NAME: $pluginMainModule", error = AssertionError(message))
}

/**
 * The content modules of every plugin of the report, by main module.
 *
 * One plugin can have a report per OS and per arch, so the value collects the content modules of all of them.
 */
private fun collectContentModulesByMainModule(content: ParsedContentReport): Map<String, Set<String>> {
  val result = LinkedHashMap<String, MutableSet<String>>()
  for (report in content.bundled + content.nonBundled) {
    val contentModules = result.computeIfAbsent(report.mainModule) { LinkedHashSet() }
    for (entry in report.content) {
      entry.contentModules.mapTo(contentModules) { it.name }
    }
  }
  return result
}
