// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import io.opentelemetry.api.trace.Span
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.JarPackagerDependencyHelper

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
  plugin: PluginLayout,
  releaseDate: String,
  releaseVersion: String,
  pluginsToPublish: Set<PluginLayout?>,
  helper: JarPackagerDependencyHelper,
  platformLayout: PlatformLayout,
  context: BuildContext,
) {
  val pluginModule = context.findRequiredModule(plugin.mainModule)
  val descriptorContent = plugin.rawPluginXmlPatcher(helper.getPluginXmlContent(pluginModule), context)

  val includeInBuiltinCustomRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                         context.proprietaryBuildTools.artifactsServer != null
  val isBundled = !pluginsToPublish.contains(plugin)
  val compatibleBuildRange = when {
    isBundled || plugin.pluginCompatibilityExactVersion || includeInBuiltinCustomRepository -> CompatibleBuildRange.EXACT
    context.applicationInfo.isEAP || plugin.pluginCompatibilitySameRelease -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val pluginVersion = plugin.versionEvaluator.evaluate(pluginXmlSupplier = { descriptorContent }, ideBuildVersion = context.pluginBuildNumber, context = context)
  @Suppress("TestOnlyProblems")
  val content = try {
    val element = doPatchPluginXml(
      rootElement = JDOMUtil.load(descriptorContent),
      pluginModuleName = plugin.mainModule,
      pluginVersion = pluginVersion.pluginVersion,
      releaseDate = releaseDate,
      releaseVersion = releaseVersion,
      compatibleSinceUntil = pluginVersion.sinceUntil ?: getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber),
      toPublish = pluginsToPublish.contains(plugin),
      retainProductDescriptorForBundledPlugin = plugin.retainProductDescriptorForBundledPlugin,
      isEap = context.applicationInfo.isEAP,
    )

    embedContentModules(
      xml = element,
      file = findFileInModuleSources(module = pluginModule, relativePath = "META-INF/plugin.xml")!!,
      xIncludePathResolver = createXIncludePathResolver(plugin.includedModules.map { it.moduleName } + platformLayout.includedModules.map { it.moduleName }, context),
      layout = plugin,
      context = context,
    )

    plugin.pluginXmlPatcher(JDOMUtil.write(element), context)
  }
  catch (e: Throwable) {
    throw RuntimeException("Could not patch descriptor (module=${plugin.mainModule})", e)
  }
  // os-specific plugins being built several times - we expect that plugin.xml must be the same
  moduleOutputPatcher.patchModuleOutput(moduleName = plugin.mainModule, path = "META-INF/plugin.xml", content = content, overwrite = PatchOverwriteMode.IF_EQUAL)
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
): Element {
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

  // patch Database plugin for WebStorm, see WEB-48278
  if (toPublish && productDescriptor != null && productDescriptor.getAttributeValue("code") == "PDB") {
    Span.current().addEvent("patch $pluginModuleName for WebStorm")
    val pluginName = rootElement.getChild("name")
    check(pluginName.text == "Database Tools and SQL") { "Plugin name for \'$pluginModuleName\' should be \'Database Tools and SQL\'" }
    pluginName.text = "Database Tools and SQL for WebStorm"
    val description = rootElement.getChild("description")
    val replaced1 = replaceInElementText(element = description, oldText = "IntelliJ-based IDEs", newText = "WebStorm")
    check(replaced1) { "Could not find \'IntelliJ-based IDEs\' in plugin description of $pluginModuleName" }

    val oldText = "The plugin provides all the same features as <a href=\"https://www.jetbrains.com/datagrip/\">DataGrip</a>, the standalone JetBrains IDE for databases."
    val replaced2 = replaceInElementText(
      element = description,
      oldText = oldText,
      newText = """
        The plugin provides all the same features as <a href="https://www.jetbrains.com/datagrip/">DataGrip</a>, the standalone JetBrains IDE for databases.
        Owners of an active DataGrip subscription can download the plugin for free.
        The plugin is also included in <a href="https://www.jetbrains.com/all/">All Products Pack</a> and <a href="https://www.jetbrains.com/community/education/">Student Pack</a>.
      """.trimIndent()
    )
    check(replaced2) { "Could not find \'$oldText\' in plugin description of $pluginModuleName" }
  }
  return rootElement
}

fun getOrCreateTopElement(rootElement: Element, tagName: String, anchors: List<String>): Element {
  rootElement.getChild(tagName)?.let {
    return it
  }

  val newElement = Element(tagName)
  val anchor = anchors.asSequence().mapNotNull { rootElement.getChild(it) }.firstOrNull()
  if (anchor == null) {
    rootElement.addContent(0, newElement)
  }
  else {
    val anchorIndex = rootElement.indexOf(anchor)
    // should not happen
    check(anchorIndex >= 0) {
      "anchor < 0 when getting child index of \'${anchor.name}\' in root element of ${JDOMUtil.write(rootElement)}"
    }
    rootElement.addContent(anchorIndex + 1, newElement)
  }
  return newElement
}

@Suppress("SameParameterValue")
private fun replaceInElementText(element: Element, oldText: String, newText: String): Boolean {
  val textBefore = element.text
  val text = textBefore.replace(oldText, newText)
  if (textBefore == text) {
    return false
  }

  element.text = text
  return true
}

private fun setProductDescriptorEapAttribute(productDescriptor: Element, isEap: Boolean) {
  if (isEap) {
    productDescriptor.setAttribute("eap", "true")
  }
  else {
    productDescriptor.removeAttribute("eap")
  }
}
