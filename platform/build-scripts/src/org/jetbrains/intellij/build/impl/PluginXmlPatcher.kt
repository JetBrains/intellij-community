// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.text.SemVer
import io.opentelemetry.api.trace.Span
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.embedContentModule
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent

private val buildNumberRegex = Regex("""(\d+\.)+\d+""")
private val digitDotDigitRegex = Regex("""\d+\.\d+""")

fun getCompatiblePlatformVersionRange(compatibleBuildRange: CompatibleBuildRange, buildNumber: String): Pair<String, String> {
  if (compatibleBuildRange == CompatibleBuildRange.EXACT || !buildNumber.matches(buildNumberRegex)) {
    return Pair(buildNumber, buildNumber)
  }

  val sinceBuild: String
  val untilBuild: String
  if (compatibleBuildRange == CompatibleBuildRange.ANY_WITH_SAME_BASELINE) {
    sinceBuild = buildNumber.substring(0, buildNumber.indexOf("."))
    untilBuild = buildNumber.substring(0, buildNumber.indexOf(".")) + ".*"
  }
  else {
    sinceBuild = if (buildNumber.matches(digitDotDigitRegex)) buildNumber else buildNumber.substring(0, buildNumber.lastIndexOf("."))
    val end = if ((compatibleBuildRange == CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE)) {
      if (buildNumber.matches(digitDotDigitRegex)) buildNumber.length else buildNumber.lastIndexOf(".")
    }
    else {
      buildNumber.indexOf('.')
    }
    untilBuild = "${buildNumber.substring(0, end)}.*"
  }
  return Pair(sinceBuild, untilBuild)
}

internal suspend fun patchPluginXml(
  moduleOutputPatcher: ModuleOutputPatcher,
  platformLayout: PlatformLayout,
  pluginLayout: PluginLayout,
  releaseDate: String,
  releaseVersion: String,
  pluginsToPublish: Set<PluginLayout?>,
  context: BuildContext,
  platformDescriptorCache: ScopedCachedDescriptorContainer,
  pluginDescriptorCache: ScopedCachedDescriptorContainer,
) {
  val pluginModule = context.findRequiredModule(pluginLayout.mainModule)
  val descriptorContent = pluginLayout.rawPluginXmlPatcher(getUnprocessedPluginXmlContent(pluginModule, context).decodeToString(), context)

  val includeInBuiltinCustomRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                         context.proprietaryBuildTools.artifactsServer != null
  val isBundled = !pluginsToPublish.contains(pluginLayout)
  val compatibleBuildRange = context.productProperties.customCompatibleBuildRange ?: when {
    isBundled || pluginLayout.pluginCompatibilityExactVersion || includeInBuiltinCustomRepository -> CompatibleBuildRange.EXACT
    context.applicationInfo.isEAP || pluginLayout.pluginCompatibilitySameRelease -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val pluginVersion = getPluginVersion(plugin = pluginLayout, descriptorContent = descriptorContent, context = context)
  @Suppress("TestOnlyProblems")
  val content = try {
    val element = JDOMUtil.load(descriptorContent)
    doPatchPluginXml(
      rootElement = element,
      pluginModuleName = pluginLayout.mainModule,
      pluginVersion = pluginVersion.pluginVersion,
      releaseDate = releaseDate,
      releaseVersion = releaseVersion,
      compatibleSinceUntil = pluginVersion.sinceUntil ?: getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber),
      toPublish = pluginsToPublish.contains(pluginLayout),
      retainProductDescriptorForBundledPlugin = pluginLayout.retainProductDescriptorForBundledPlugin,
      isEap = context.applicationInfo.isEAP,
    )

    // see comment in productModuleLayout
    val xIncludeResolver = XIncludeElementResolverImpl(
      searchPath = listOf(
        DescriptorSearchScope(pluginLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, pluginDescriptorCache),
        DescriptorSearchScope(platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, platformDescriptorCache),
      ),
      context = context)
    resolveIncludes(element = element, elementResolver = xIncludeResolver)

    val dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
    val frontendModuleFilter = context.getFrontendModuleFilter()
    filterAndProcessContentModules(rootElement = element, pluginMainModuleName = pluginLayout.mainModule, context = context) { moduleElement, moduleName, _ ->
      if (!pluginLayout.pathsToScramble.isEmpty()) {
        return@filterAndProcessContentModules
      }

      embedContentModule(
        moduleElement = moduleElement,
        pluginDescriptorContainer = pluginDescriptorCache,
        xIncludeResolver = xIncludeResolver,
        context = context,
        moduleName = moduleName,
        dependencyHelper = dependencyHelper,
        pluginLayout = pluginLayout,
        frontendModuleFilter = frontendModuleFilter
      )
    }
    pluginLayout.pluginXmlPatcher(JDOMUtil.write(element), context)
  }
  catch (e: Throwable) {
    throw RuntimeException("Could not patch descriptor (module=${pluginLayout.mainModule})", e)
  }
  // OS-specific plugins being built several times - we expect that plugin.xml must be the same
  moduleOutputPatcher.patchModuleOutput(moduleName = pluginLayout.mainModule, path = PLUGIN_XML_RELATIVE_PATH, content = content, overwrite = PatchOverwriteMode.IF_EQUAL)
  pluginDescriptorCache.put(PLUGIN_XML_RELATIVE_PATH, content.toByteArray())
}

private val DEV_BUILD_SCHEME: Regex = Regex("^${SnapshotBuildNumber.BASE.replace(".", "\\.")}\\.(SNAPSHOT|[0-9]+)$")

private suspend fun getPluginVersion(plugin: PluginLayout, descriptorContent: String, context: BuildContext): PluginVersionEvaluatorResult {
  val pluginVersion = plugin.versionEvaluator.evaluate(pluginXmlSupplier = { descriptorContent }, ideBuildVersion = context.pluginBuildNumber, context = context)
  check(
    !plugin.semanticVersioning ||
    SemVer.parseFromText(pluginVersion.pluginVersion) != null ||
    DEV_BUILD_SCHEME.matches(pluginVersion.pluginVersion)
  ) {
    "$plugin version '${pluginVersion.pluginVersion}' is expected to match either '$DEV_BUILD_SCHEME' or the Semantic Versioning, see https://semver.org"
  }
  return pluginVersion
}

@TestOnly
fun doPatchPluginXml(
  rootElement: Element,
  pluginModuleName: String,
  pluginVersion: String?,
  releaseDate: String,
  releaseVersion: String,
  compatibleSinceUntil: Pair<String, String>,
  toPublish: Boolean,
  retainProductDescriptorForBundledPlugin: Boolean,
  isEap: Boolean,
) {
  val ideaVersionElement = getOrCreateTopElement(rootElement, "idea-version", listOf("id", "name"))
  ideaVersionElement.setAttribute("since-build", compatibleSinceUntil.first)
  ideaVersionElement.setAttribute("until-build", compatibleSinceUntil.second)
  val versionElement = getOrCreateTopElement(rootElement, "version", listOf("id", "name"))
  versionElement.text = pluginVersion
  val productDescriptor = rootElement.getChild("product-descriptor")
  if (productDescriptor != null) {
    if (!toPublish && !retainProductDescriptorForBundledPlugin) {
      Span.current().addEvent("skip $pluginModuleName <product-descriptor/>")
      productDescriptor.detach()
    }
    else {
      Span.current().addEvent("patch $pluginModuleName <product-descriptor/>")

      setProductDescriptorEapAttribute(productDescriptor, isEap)
      val overriddenReleaseDate = productDescriptor.getAttribute("release-date")
        ?.value?.takeUnless { it.startsWith("__") }
      if (overriddenReleaseDate == null) {
        productDescriptor.setAttribute("release-date", releaseDate)
      }
      productDescriptor.setAttribute("release-version", releaseVersion)
    }
  }

  // CDATA is not created by our XML reader, so, we restore wrapping into CDATA
  for (name in arrayOf("description", "change-notes")) {
    rootElement.getChild(name)?.let {
      val text = it.text
      if (text.isNotEmpty()) {
        it.setContent(CDATA(text))
      }
    }
  }
}

fun getOrCreateTopElement(rootElement: Element, tagName: String, anchors: List<String>): Element {
  rootElement.getChild(tagName)?.let {
    return it
  }

  val newElement = Element(tagName)
  val anchor = anchors.firstNotNullOfOrNull { rootElement.getChild(it) }
  if (anchor == null) {
    rootElement.addContent(0, newElement)
  }
  else {
    val anchorIndex = rootElement.indexOf(anchor)
    // should not happen
    check(anchorIndex >= 0) {
      "anchor < 0 when getting child index of '${anchor.name}' in root element of ${JDOMUtil.write(rootElement)}"
    }
    rootElement.addContent(anchorIndex + 1, newElement)
  }
  return newElement
}

private fun setProductDescriptorEapAttribute(productDescriptor: Element, isEap: Boolean) {
  if (isEap) {
    productDescriptor.setAttribute("eap", "true")
  }
  else {
    productDescriptor.removeAttribute("eap")
  }
}