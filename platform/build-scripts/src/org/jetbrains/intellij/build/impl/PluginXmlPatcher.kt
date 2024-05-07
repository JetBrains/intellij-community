// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.JarPackagerDependencyHelper

private val buildNumberRegex = Regex("(\\d+\\.)+\\d+")

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
    sinceBuild = if (buildNumber.matches(Regex("\\d+\\.\\d+"))) buildNumber else buildNumber.substring(0, buildNumber.lastIndexOf("."))
    val end = if ((compatibleBuildRange == CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE)) {
      if (buildNumber.matches(Regex("\\d+\\.\\d+"))) buildNumber.length else buildNumber.lastIndexOf(".")
    }
    else {
      buildNumber.indexOf(".")
    }
    untilBuild = "${buildNumber.substring(0, end)}.*"
  }
  return Pair(sinceBuild, untilBuild)
}

internal fun patchPluginXml(
  moduleOutputPatcher: ModuleOutputPatcher,
  plugin: PluginLayout,
  releaseDate: String,
  releaseVersion: String,
  pluginsToPublish: Set<PluginLayout?>,
  context: BuildContext,
  helper: JarPackagerDependencyHelper,
) {
  val pluginModule = context.findRequiredModule(plugin.mainModule)
  val descriptorContent = helper.getPluginXmlContent(pluginModule)

  val includeInBuiltinCustomRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                         context.proprietaryBuildTools.artifactsServer != null
  val isBundled = !pluginsToPublish.contains(plugin)
  val compatibleBuildRange = when {
    isBundled || plugin.pluginCompatibilityExactVersion || includeInBuiltinCustomRepository -> CompatibleBuildRange.EXACT
    context.applicationInfo.isEAP || plugin.pluginCompatibilitySameRelease -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val pluginVersion = plugin.versionEvaluator.evaluate(pluginXmlSupplier = { descriptorContent }, ideBuildVersion = context.pluginBuildNumber, context = context)
  val sinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber)
  @Suppress("TestOnlyProblems") val content = try {
    plugin.pluginXmlPatcher(
      doPatchPluginXml(
        rootElement = JDOMUtil.load(descriptorContent),
        pluginModuleName = plugin.mainModule,
        pluginVersion = pluginVersion,
        releaseDate = releaseDate,
        releaseVersion = releaseVersion,
        compatibleSinceUntil = sinceUntil,
        toPublish = pluginsToPublish.contains(plugin),
        retainProductDescriptorForBundledPlugin = plugin.retainProductDescriptorForBundledPlugin,
        isEap = context.applicationInfo.isEAP,
        productName = context.applicationInfo.fullProductName,
      ),
      context,
    )
  }
  catch (e: Throwable) {
    throw RuntimeException("Could not patch descriptor (module=${pluginModule.name})", e)
  }
  moduleOutputPatcher.patchModuleOutput(plugin.mainModule, "META-INF/plugin.xml", content)
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
  productName: String,
): String {
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
      productDescriptor.setAttribute("release-date", releaseDate)
      productDescriptor.setAttribute("release-version", releaseVersion)
    }
  }

  // patch Database plugin for WebStorm, see WEB-48278
  if (toPublish && productDescriptor != null && productDescriptor.getAttributeValue("code") == "PDB" && productName == "WebStorm") {
    Span.current().addEvent("patch $pluginModuleName for WebStorm")
    val pluginName = rootElement.getChild("name")
    check(pluginName.text == "Database Tools and SQL") { "Plugin name for \'$pluginModuleName\' should be \'Database Tools and SQL\'" }
    pluginName.text = "Database Tools and SQL for WebStorm"
    val description = rootElement.getChild("description")
    val replaced = replaceInElementText(description, "IntelliJ-based IDEs", "WebStorm")
    check(replaced) { "Could not find \'IntelliJ-based IDEs\' in plugin description of $pluginModuleName" }
  }
  return JDOMUtil.write(rootElement)
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
  var replaced = false
  for (node in element.content) {
    if (node is Text) {
      val textBefore = node.text
      val text = textBefore.replace(oldText, newText)
      if (textBefore != text) {
        replaced = true
        node.text = text
      }
    }
  }
  return replaced
}

private fun setProductDescriptorEapAttribute(productDescriptor: Element, isEap: Boolean) {
  if (isEap) {
    productDescriptor.setAttribute("eap", "true")
  }
  else {
    productDescriptor.removeAttribute("eap")
  }
}
