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
import org.jetbrains.intellij.build.dev.DevDistDescriptorStage
import org.jetbrains.intellij.build.dev.DevDistDescriptorStages
import org.jetbrains.intellij.build.dev.DevDistPatchedDescriptors
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
 * The patch has two producers. The assembly builds this request from the product layout. A packing action reads it from
 * a generated plan, with no JPS project model and no product layout. So the type holds no build context, no plugin
 * layout and no platform layout, and the shared body cannot reach one through it.
 */
internal class PluginDescriptorPatchRequest(
  /** The plugin's main module, which the descriptor belongs to. */
  @JvmField val mainModule: String,
  /** The plugin's directory under `plugins/`. Reported by `DevDistPatchedDescriptors` only. */
  @JvmField val directoryName: String,
  /** The main jar's name as the layout declares it. Reported by `DevDistPatchedDescriptors` only. */
  @JvmField val mainJarName: String,
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
  /** Whether the content-module stage was allowed to run. Reported by `DevDistPatchedDescriptors` only. */
  @JvmField val embedsContentModules: Boolean,
)

/**
 * Applies the descriptor patch and returns the text the plugin's main jar receives.
 *
 * This is the body both producers of a patched descriptor share. One body means the two cannot disagree, so a byte
 * comparison of their outputs guards the [request] and not the code.
 *
 * @param embedContentModules the content-module stage. It is not data: it runs over the element this body parsed, and
 *   the assembly decides which `<module/>` survives with a filter that reads the JPS project model.
 * @param patchText the last stage. It is not data for the same reason: it runs over the text this body produced.
 */
internal suspend fun applyPluginDescriptorPatch(
  request: PluginDescriptorPatchRequest,
  xIncludeResolver: XIncludeElementResolverImpl,
  stages: DevDistDescriptorStages?,
  embedContentModules: suspend (rootElement: Element) -> Unit,
  patchText: (text: String) -> String,
): String {
  stages?.add(DevDistDescriptorStage.SOURCE, request.sourceContent)
  stages?.add(DevDistDescriptorStage.RAW_TEXT_PATCHER, request.rawPatchedContent)

  @Suppress("TestOnlyProblems")
  val content = try {
    val element = JDOMUtil.load(request.rawPatchedContent)
    stages?.add(DevDistDescriptorStage.RESERIALIZED, JDOMUtil.write(element))
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
    stages?.add(DevDistDescriptorStage.STAMPS, JDOMUtil.write(element))

    resolveIncludes(element = element, elementResolver = xIncludeResolver)
    stages?.add(DevDistDescriptorStage.INCLUDES, JDOMUtil.write(element))

    embedContentModules(element)
    val embedded = JDOMUtil.write(element)
    stages?.add(DevDistDescriptorStage.CONTENT_MODULES, embedded)
    val patched = patchText(embedded)
    stages?.add(DevDistDescriptorStage.TEXT_PATCHER, patched)
    patched
  }
  catch (e: Throwable) {
    throw RuntimeException("Could not patch descriptor (module=${request.mainModule})", e)
  }
  stages?.let {
    DevDistPatchedDescriptors.record(
      mainModule = request.mainModule,
      directoryName = request.directoryName,
      mainJar = request.mainJarName,
      embedsContentModules = request.embedsContentModules,
      stages = it,
    )
  }
  return content
}

/**
 * Builds the patch request from the product layout, runs [applyPluginDescriptorPatch], then publishes the result twice.
 *
 * Both publishes are load-bearing. The module output patch puts the text into the plugin's main jar.
 * `computeModuleSourcesByContent` reads the cached copy back, and it fails without it.
 */
internal suspend fun patchPluginXml(
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
  // What this patch does to the descriptor, stage by stage, when a dev assembly was asked for it.
  // See `DevDistPatchedDescriptors`.
  val stages = DevDistPatchedDescriptors.stagesOrNull()
  val sourceContent = getUnprocessedPluginXmlContent(pluginModule, context.outputProvider).decodeToString()
  val descriptorContent = pluginLayout.rawPluginXmlPatcher(sourceContent, context)

  val compatibleBuildRange = context.productProperties.customCompatibleBuildRange ?: when {
    pluginLayout.pluginCompatibilityExactVersion || isIncludePluginsInBuiltinCustomRepository(context) -> CompatibleBuildRange.EXACT
    context.applicationInfo.isEAP || pluginLayout.pluginCompatibilitySameRelease -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val pluginVersion = getPluginVersion(plugin = pluginLayout, descriptorContent = descriptorContent, context = context)
  // The embedding stage runs per `<module/>`, and a layout that scrambles paths returns from every one of them. Decided
  // once here, so that the report states the decision the run made and not a second computation over the layout.
  val embedsContentModules = pluginLayout.pathsToScramble.isEmpty()

  // see comment in productModuleLayout
  val xIncludeResolver = XIncludeElementResolverImpl(
    searchPath = listOf(
      DescriptorSearchScope(pluginLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, pluginDescriptorCache),
      DescriptorSearchScope(platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, platformDescriptorCache),
    ),
    context = descriptorResolveContext(context),
  )

  val content = applyPluginDescriptorPatch(
    request = PluginDescriptorPatchRequest(
      mainModule = pluginLayout.mainModule,
      directoryName = pluginLayout.directoryName,
      mainJarName = pluginLayout.getMainJarName(),
      sourceContent = sourceContent,
      rawPatchedContent = descriptorContent,
      pluginVersion = pluginVersion.pluginVersion,
      compatibleSinceUntil = pluginVersion.sinceUntil ?: getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber),
      releaseDate = releaseDate,
      releaseVersion = releaseVersion,
      toPublish = pluginsToPublish.contains(pluginLayout),
      retainProductDescriptorForBundledPlugin = pluginLayout.retainProductDescriptorForBundledPlugin,
      isEap = context.applicationInfo.isEAP,
      embedsContentModules = embedsContentModules,
    ),
    xIncludeResolver = xIncludeResolver,
    stages = stages,
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
  // OS-specific plugins being built several times - we expect that plugin.xml must be the same
  moduleOutputPatcher.patchModuleOutput(moduleName = pluginLayout.mainModule, path = PLUGIN_XML_RELATIVE_PATH, content = content, overwrite = PatchOverwriteMode.IF_EQUAL)
  pluginDescriptorCache.put(PLUGIN_XML_RELATIVE_PATH, content.toByteArray())
}

internal fun isIncludePluginsInBuiltinCustomRepository(context: BuildContext): Boolean {
  return context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
         context.proprietaryBuildTools.artifactsServer != null
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
