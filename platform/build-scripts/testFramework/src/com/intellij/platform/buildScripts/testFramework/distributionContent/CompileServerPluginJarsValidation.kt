// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider

/**
 * Checks that each packed plugin contains the jars that its `compileServer.plugin` extensions name.
 *
 * The IDE adds these jars to the classpath of the build process. In a packed IDE, `findClassesRoot` in
 * `BuildProcessClasspathManager` looks only for `lib/<path>` in the plugin directory. When it does not exist, the IDE
 * logs "Cannot add '<jar>' ... to compiler classpath", and the build process runs without the JPS part of the plugin.
 *
 * The jar names come from the plugin layout, and regular tests do not see them. This validation reads them from the
 * content report of the packed plugin.
 *
 * The validation reads the descriptors from the module output, not from the packed plugin. A layout that patches the
 * extension out of the packed descriptor thus gets a false failure. [ignoredPlugins] maps the main module of such a
 * plugin to the reason.
 */
@Internal
fun createCompileServerPluginJarsValidation(
  targetId: String,
  ignoredPlugins: Map<String, String> = emptyMap(),
): PackagingTargetValidationSpec = PackagingTargetValidationSpec(
  targetId = targetId,
  name = "compile-server-plugin-jars",
  problemMessage = "a plugin does not contain a jar that its compileServer.plugin extension names",
  validator = { context ->
    (val bundled, val nonBundled) = context.content()
    collectCompileServerPluginJarFailures(
      reports = bundled + nonBundled,
      readDescriptors = { moduleName, isMainModule -> readModuleDescriptors(context.outputProvider, moduleName, isMainModule) },
      ignoredPlugins = ignoredPlugins,
    )
  },
)

/**
 * Returns one failure for each plugin of [reports] that does not contain a jar that its descriptors name.
 *
 * [readDescriptors] returns the descriptor files of one module that the plugin packs. The plugins come in main module
 * order. The variants of one plugin for each operating system are checked as one plugin.
 *
 * A plugin of [ignoredPlugins] is not reported. An ignored plugin that has no missing jar gives one more failure, so
 * that the list does not go stale.
 */
@Internal
fun collectCompileServerPluginJarFailures(
  reports: List<PluginContentReport>,
  readDescriptors: (moduleName: String, isMainModule: Boolean) -> List<ByteArray>,
  ignoredPlugins: Map<String, String> = emptyMap(),
): List<PackagingCheckFailure> {
  val checks = reports.groupBy { it.mainModule }.toSortedMap().map { entry -> checkPlugin(entry.key, entry.value, readDescriptors) }
  val [ignoredChecks, activeChecks] = checks.partition { ignoredPlugins.containsKey(it.mainModule) }
  val failures = activeChecks.filter { it.missingJars.isNotEmpty() }.map { it.toFailure() }
  val staleIgnoredPlugins = ignoredChecks
    .filter { it.missingJars.isEmpty() }
    .map { "${it.mainModule} (${ignoredPlugins.get(it.mainModule)})" }
  return if (staleIgnoredPlugins.isEmpty()) failures else failures + staleIgnoredPluginsFailure(staleIgnoredPlugins)
}

private const val LIB_PREFIX = "lib/"

private const val REPAIR =
  "The IDE then cannot start the JPS part of the plugin in the build process. Give the module that holds the JPS" +
  " classes the jar name that the extension states in the plugin layout, or change the extension to the packed jar."

private class PluginCheck(
  @JvmField val mainModule: String,
  @JvmField val packedFiles: Set<String>,
  @JvmField val missingJars: List<String>,
) {
  fun toFailure(): PackagingCheckFailure = PackagingCheckFailure(
    name = "compile-server-plugin-jars: $mainModule",
    error = AssertionError(
      "Plugin '$mainModule' does not contain the jars that its compileServer.plugin extensions name:\n" +
      missingJars.joinToString(separator = "\n") { "  $it" } +
      "\nPacked jars:\n" +
      packedFiles.filter { it.startsWith(LIB_PREFIX) }.sorted().joinToString(separator = "\n") { "  $it" } +
      "\n" + REPAIR
    ),
  )
}

private fun checkPlugin(
  mainModule: String,
  variants: List<PluginContentReport>,
  readDescriptors: (moduleName: String, isMainModule: Boolean) -> List<ByteArray>,
): PluginCheck {
  val entries = mergePerOsPluginContent(variants)
  val packedFiles = entries.mapTo(HashSet()) { it.name }
  val moduleNames = sequenceOf(mainModule) + entries.asSequence().flatMap { entry ->
    entry.modules.asSequence().map { it.name } + entry.contentModules.asSequence().map { it.name }
  }
  val missingJars = moduleNames.distinct().flatMap { moduleName ->
    readDescriptors(moduleName, moduleName == mainModule).asSequence()
      .flatMap { compileServerClasspath(readXmlAsModel(it)) }
      .filter { LIB_PREFIX + it !in packedFiles }
      .map { "$it (declared in $moduleName)" }
  }.toList()
  return PluginCheck(mainModule = mainModule, packedFiles = packedFiles, missingJars = missingJars)
}

private fun staleIgnoredPluginsFailure(staleIgnoredPlugins: List<String>): PackagingCheckFailure = PackagingCheckFailure(
  name = "compile-server-plugin-jars: stale ignored plugins",
  error = AssertionError(
    "These ignored plugins contain every jar that their compileServer.plugin extensions name:\n" +
    staleIgnoredPlugins.joinToString(separator = "\n") { "  $it" } +
    "\nRemove them from the ignored plugins of the validation."
  ),
)

private fun compileServerClasspath(root: XmlElement): Sequence<String> {
  return root.children("extensions")
    .flatMap { extensions ->
      val namespace = extensions.getAttributeValue("defaultExtensionNs")
      extensions.children.asSequence().filter { isCompileServerPlugin(namespace, it.name) }
    }
    .mapNotNull { it.getAttributeValue("classpath") }
    .flatMap { it.splitToSequence(';') }
    .filter { it.isNotEmpty() }
}

private fun isCompileServerPlugin(namespace: String?, elementName: String): Boolean {
  return when (namespace) {
    "com.intellij" -> elementName == "compileServer.plugin"
    "com.intellij.compileServer" -> elementName == "plugin"
    else -> elementName == "com.intellij.compileServer.plugin"
  }
}

private fun readModuleDescriptors(outputProvider: ModuleOutputProvider, moduleName: String, isMainModule: Boolean): List<ByteArray> {
  val module = outputProvider.findModule(moduleName) ?: return emptyList()
  val paths = if (isMainModule) listOf("META-INF/plugin.xml", "$moduleName.xml") else listOf("$moduleName.xml")
  return paths.mapNotNull { outputProvider.readFileContentFromModuleOutput(module, it) }
}
