// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.text.SemVer
import io.opentelemetry.api.trace.Span
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.FileSource
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.descriptorResolveContext
import org.jetbrains.intellij.build.classPath.embedContentModule
import org.jetbrains.intellij.build.classPath.resolveIncludes
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent
import java.nio.file.Files
import java.nio.file.Path

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
 * Refuses a produced descriptor whose stamps are not the ones this assembly computed.
 *
 * Three plain substrings, and no XML parse: a parse here would be the work the produced file exists to remove. No gate
 * compares a produced descriptor with a text this fragment computed. So this check is the one runtime statement that
 * the plan's product scalars agree with the assembly.
 *
 * It covers `<version>`, `since-build` and `until-build`. It cannot cover `release_date`, `release_version` or
 * `retain_product_descriptor`: those reach a `<product-descriptor>`, and no source of this population states one.
 */
internal fun checkProducedPluginDescriptor(
  mainModule: String,
  content: String,
  pluginVersion: String?,
  compatibleSinceUntil: Pair<String, String>,
) {
  if (pluginVersion != null) {
    check(content.contains("<version>$pluginVersion</version>")) {
      "The produced descriptor of '$mainModule' does not state the version this assembly computed ('$pluginVersion')"
    }
  }
  for ((attribute, value) in listOf("since-build" to compatibleSinceUntil.first, "until-build" to compatibleSinceUntil.second)) {
    check(content.contains("$attribute=\"$value\"")) {
      "The produced descriptor of '$mainModule' does not state $attribute='$value', which this assembly computed"
    }
  }
}

/**
 * Builds the patch request from the product layout, runs [applyPluginDescriptorPatch], then publishes the result through
 * [publishPatchedPluginXml].
 *
 * ### Two producers of the text, one publisher of it
 *
 * A fragment that was handed a produced descriptor reads that file and runs no stage of the patch. Which plugins those
 * are is the fragment's `patched_descriptors` declaration, and a plugin it does not name takes the computed path
 * unchanged. What the two paths publish, and how, is the same - see [publishPatchedPluginXml].
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

  // The other producer of this text. A declared descriptor is the file a `dev_dist_plugin_descriptor` action wrote, and
  // reading it is what this fragment does instead of parsing the descriptor, resolving its includes and embedding its
  // content modules. The seam sits here rather than at the module lookup, because everything above it is data the
  // produced path needs as well. The version and the compatibility range are what `checkProducedPluginDescriptor` holds
  // the file to.
  val producedDescriptor = BazelBuildInputs.producedPluginDescriptorIfDeclared(pluginLayout.mainModule)
  if (producedDescriptor != null) {
    val produced = Files.readString(producedDescriptor)
    checkProducedPluginDescriptor(
      mainModule = pluginLayout.mainModule,
      content = produced,
      pluginVersion = pluginVersion.pluginVersion,
      compatibleSinceUntil = compatibleSinceUntil,
    )
    publishPatchedPluginXml(
      moduleOutputPatcher = moduleOutputPatcher,
      pluginDescriptorCache = pluginDescriptorCache,
      mainModule = pluginLayout.mainModule,
      content = produced,
      producedFile = producedDescriptor,
    )
    return
  }

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
 * Publishes the patched descriptor. Both paths of [patchPluginXml] publish it this way.
 *
 * Both publishes are load-bearing, and the second one is the quiet one. The module output patch puts the text into the
 * plugin's main jar. The cache is read back by `computeModuleSourcesByContent`, by the scramble path and by the
 * module-repository header, each of which fails without it, and by
 * `getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader`, which falls back to the **unpatched** source text - a
 * class-load failure at IDE start rather than a diff.
 *
 * `IF_EQUAL` holds for a produced descriptor more strongly than for a computed one: the file is one manifest entry
 * resolved to one path, so two reads of it are the same bytes by construction.
 *
 * ### Which channel the jar takes the descriptor from
 *
 * [producedFile] is the file a `dev_dist_plugin_descriptor` action wrote, when this plugin was handed one. The jar then
 * takes the descriptor as a [FileSource], so the executed recipe states its label instead of `inMemory`, and the replay
 * gate can pack the jar again from the recipe alone. A computed text has no file to name, so it keeps the byte channel.
 *
 * The jar bytes do not change with the channel. A plugin jar is built with `compress = false`, so `buildJar` passes no
 * `Deflater` and `ZipFileWriter.file` stores the entry rather than deflating it, which is what `uncompressedData` does
 * for the byte channel. The jar cache's digest does change - `FileSourceCacheStrategy` hashes the path and the content
 * hash - so the first build after this re-packs the main jar of every plugin that reads a produced descriptor.
 *
 * The cache publish stays on the text either way. Its readers want bytes, not a path, and one of them falls back to the
 * unpatched source when the entry is missing.
 */
private fun publishPatchedPluginXml(
  moduleOutputPatcher: ModuleOutputPatcher,
  pluginDescriptorCache: ScopedCachedDescriptorContainer,
  mainModule: String,
  content: String,
  producedFile: Path? = null,
) {
  if (producedFile == null) {
    // OS-specific plugins being built several times - we expect that plugin.xml must be the same
    moduleOutputPatcher.patchModuleOutput(moduleName = mainModule, path = PLUGIN_XML_RELATIVE_PATH, content = content, overwrite = PatchOverwriteMode.IF_EQUAL)
  }
  else {
    val bytes = content.toByteArray()
    moduleOutputPatcher.patchModuleOutputWithFile(
      moduleName = mainModule,
      path = PLUGIN_XML_RELATIVE_PATH,
      source = FileSource(
        relativePath = PLUGIN_XML_RELATIVE_PATH,
        size = bytes.size,
        hash = Hashing.xxh3_64().hashBytesToLong(bytes),
        file = producedFile,
      ),
    )
  }
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
