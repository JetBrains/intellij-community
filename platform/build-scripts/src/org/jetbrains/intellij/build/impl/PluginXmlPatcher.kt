// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.text.SemVer
import io.opentelemetry.api.trace.Span
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.descriptorResolveContext
import org.jetbrains.intellij.build.classPath.embedContentModule
import org.jetbrains.intellij.build.classPath.resolveIncludes
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

/**
 * Every fact [applyPluginDescriptorPatch] needs, as data.
 *
 * The assembly builds this request from the product layout. The Go patcher of `dev_dist_plugin_descriptor` is a port of
 * the same patch. It reads a generated plan, with no JPS project model and no product layout. So the type holds no
 * build context, no plugin layout and no platform layout, and the body cannot reach one through it.
 */
internal class PluginDescriptorPatchRequest(
  /** The plugin's main module, which the descriptor belongs to. */
  @JvmField val mainModule: String,
  /** The descriptor as the plugin's main module output holds it. */
  @JvmField val sourceContent: String,
  /** [sourceContent] after the raw text patch of the layout. Equal to [sourceContent] when there is no such patch. */
  @JvmField val rawPatchedContent: String,
  @JvmField val pluginVersion: String?,
  @JvmField val compatibleSinceUntil: Pair<String, String>,
  @JvmField val releaseDate: String,
  @JvmField val releaseVersion: String,
  @JvmField val toPublish: Boolean,
  @JvmField val retainProductDescriptorForBundledPlugin: Boolean,
  @JvmField val isEap: Boolean,
  /** Whether XML normalization must finish before embedded content descriptors add CDATA. */
  @JvmField val reserializeBeforeContentEmbedding: Boolean = false,
)

/**
 * Applies the descriptor patch and returns the text the plugin's main jar receives.
 *
 * This body has one caller, the assembly. The Go patcher of `dev_dist_plugin_descriptor` is a port of it and produces
 * the same text for the dev distribution.
 *
 * @param embedContentModules the content-module stage. It is not data: it runs over the element this body parsed, and
 *   the assembly decides which `<module/>` survives with a filter that reads the JPS project model.
 * @param patchText the last stage. It is not data for the same reason: it runs over the text this body produced.
 */
internal fun applyPluginDescriptorPatch(
  request: PluginDescriptorPatchRequest,
  xIncludeResolver: XIncludeElementResolverImpl,
  embedContentModules: (rootElement: Element) -> Unit,
  patchText: (text: String) -> String,
): String {
  @Suppress("TestOnlyProblems")
  val content = try {
    var element = JDOMUtil.load(request.rawPatchedContent)
    doPatchPluginXml(
      rootElement = element,
      pluginModuleName = request.mainModule,
      pluginVersion = request.pluginVersion,
      releaseDate = request.releaseDate,
      releaseVersion = request.releaseVersion,
      compatibleSinceUntil = request.compatibleSinceUntil,
      toPublish = request.toPublish,
      retainProductDescriptorForBundledPlugin = request.retainProductDescriptorForBundledPlugin,
      isEap = request.isEap,
    )

    resolveIncludes(element = element, elementResolver = xIncludeResolver)

    if (request.reserializeBeforeContentEmbedding) {
      element = JDOMUtil.load(JDOMUtil.write(element))
    }
    embedContentModules(element)
    patchText(JDOMUtil.write(element))
  }
  catch (e: Throwable) {
    throw RuntimeException("Could not patch descriptor (module=${request.mainModule})", e)
  }
  return content
}

/**
 * Builds the patch request from the product layout, runs [applyPluginDescriptorPatch], then publishes the result through
 * [publishPatchedPluginXml].
 */
internal fun patchPluginXml(
  moduleOutputPatcher: ModuleOutputPatcher,
  platformLayout: PlatformLayout,
  pluginLayout: PluginLayout,
  releaseDate: String,
  releaseVersion: String,
  pluginsToPublish: Set<PluginLayout?>,
  platformDescriptorCache: ScopedCachedDescriptorContainer,
  pluginDescriptorCache: ScopedCachedDescriptorContainer,
  context: BuildContext,
) {
  val pluginModule = context.outputProvider.findRequiredModule(pluginLayout.mainModule)
  val sourceContent = getUnprocessedPluginXmlContent(pluginModule, context.outputProvider).decodeToString()
  val descriptorContent = pluginLayout.rawPluginXmlPatcher(sourceContent, context)

  val compatibleBuildRange = context.productProperties.customCompatibleBuildRange ?: when {
    pluginLayout.pluginCompatibilityExactVersion || isIncludePluginsInBuiltinCustomRepository(context) -> CompatibleBuildRange.EXACT
    context.applicationInfo.isEAP -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val pluginVersion = getPluginVersion(plugin = pluginLayout, descriptorContent = descriptorContent, context = context)
  val compatibleSinceUntil = pluginVersion.sinceUntil ?: getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber)

  // see comment in productModuleLayout
  val xIncludeResolver = XIncludeElementResolverImpl(
    searchPath = listOf(
      DescriptorSearchScope(pluginLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, pluginDescriptorCache),
      DescriptorSearchScope(platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, platformDescriptorCache),
    ),
    context = descriptorResolveContext(context),
  )

  // The embedding stage runs per `<module/>`, and a layout that scrambles paths returns from every one of them. Decided
  // once here, ahead of the loop.
  val embedsContentModules = pluginLayout.pathsToScramble.isEmpty()

  val content = applyPluginDescriptorPatch(
    request = PluginDescriptorPatchRequest(
      mainModule = pluginLayout.mainModule,
      sourceContent = sourceContent,
      rawPatchedContent = descriptorContent,
      pluginVersion = pluginVersion.pluginVersion,
      compatibleSinceUntil = compatibleSinceUntil,
      releaseDate = releaseDate,
      releaseVersion = releaseVersion,
      toPublish = pluginsToPublish.contains(pluginLayout),
      retainProductDescriptorForBundledPlugin = pluginLayout.retainProductDescriptorForBundledPlugin,
      isEap = context.applicationInfo.isEAP,
    ),
    xIncludeResolver = xIncludeResolver,
    embedContentModules = { element ->
      val dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
      val frontendModuleFilter = context.getFrontendModuleFilter()
      filterAndProcessContentModules(rootElement = element, pluginMainModuleName = pluginLayout.mainModule, context = context) { moduleElement, moduleName, _ ->
        if (!embedsContentModules) {
          return@filterAndProcessContentModules
        }

        embedContentModule(
          moduleElement = moduleElement,
          pluginDescriptorContainer = pluginDescriptorCache,
          xIncludeResolver = xIncludeResolver,
          moduleName = moduleName,
          dependencyHelper = dependencyHelper,
          pluginLayout = pluginLayout,
          frontendModuleFilter = frontendModuleFilter,
          outputProvider = context.outputProvider,
        )
      }
    },
    patchText = { pluginLayout.pluginXmlPatcher(it, context) },
  )
  publishPatchedPluginXml(
    moduleOutputPatcher = moduleOutputPatcher,
    pluginDescriptorCache = pluginDescriptorCache,
    mainModule = pluginLayout.mainModule,
    content = content,
  )
}

/**
 * Publishes the patched descriptor.
 *
 * Both publishes are load-bearing, and the second one is the quiet one. The module output patch puts the text into the
 * plugin's main jar. The cache is read back by `computeModuleSourcesByContent`, by the scramble path and by the
 * module-repository header, each of which fails without it, and by
 * `getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader`, which falls back to the **unpatched** source text - a
 * class-load failure at IDE start rather than a diff.
 */
private fun publishPatchedPluginXml(
  moduleOutputPatcher: ModuleOutputPatcher,
  pluginDescriptorCache: ScopedCachedDescriptorContainer,
  mainModule: String,
  content: String,
) {
  // OS-specific plugins being built several times - we expect that plugin.xml must be the same
  moduleOutputPatcher.patchModuleOutput(moduleName = mainModule, path = PLUGIN_XML_RELATIVE_PATH, content = content, overwrite = PatchOverwriteMode.IF_EQUAL)
  pluginDescriptorCache.put(PLUGIN_XML_RELATIVE_PATH, content.toByteArray())
}

internal fun isIncludePluginsInBuiltinCustomRepository(context: BuildContext): Boolean {
  return context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
         context.proprietaryBuildTools.artifactsServer != null
}

private val DEV_BUILD_SCHEME: Regex = Regex("^${SnapshotBuildNumber.BASE.replace(".", "\\.")}\\.(SNAPSHOT|[0-9]+)$")

private fun getPluginVersion(plugin: PluginLayout, descriptorContent: String, context: BuildContext): PluginVersionEvaluatorResult {
  val pluginVersion = plugin.versionEvaluator.evaluate(pluginXmlSupplier = { descriptorContent }, ideBuildVersion = context.pluginBuildNumber, context = context)
  validatePluginDescriptorVersion(plugin, pluginVersion.pluginVersion)
  return pluginVersion
}

internal fun validatePluginDescriptorVersion(plugin: PluginLayout, pluginVersion: String) {
  check(
    !plugin.semanticVersioning ||
    SemVer.parseFromText(pluginVersion) != null ||
    DEV_BUILD_SCHEME.matches(pluginVersion)
  ) {
    "$plugin version '$pluginVersion' is expected to match either '$DEV_BUILD_SCHEME' or the Semantic Versioning, see https://semver.org"
  }
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
