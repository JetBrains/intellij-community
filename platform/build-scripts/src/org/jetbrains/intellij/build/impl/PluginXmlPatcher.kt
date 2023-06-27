// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import de.pdark.decentxml.*
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange
import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal val pluginDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
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

internal fun patchPluginXml(moduleOutputPatcher: ModuleOutputPatcher,
                            plugin: PluginLayout,
                            releaseDate: String,
                            releaseVersion: String,
                            pluginsToPublish: Set<PluginLayout?>,
                            context: BuildContext) {
  val moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.mainModule))
  val pluginXmlFile = moduleOutput.resolve("META-INF/plugin.xml")
  if (Files.notExists(pluginXmlFile)) {
    context.messages.error("plugin.xml not found in ${plugin.mainModule} module: $pluginXmlFile")
  }

  val includeInBuiltinCustomRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                         context.proprietaryBuildTools.artifactsServer != null
  val isBundled = !pluginsToPublish.contains(plugin)
  val compatibleBuildRange = when {
    isBundled || plugin.pluginCompatibilityExactVersion || includeInBuiltinCustomRepository -> CompatibleBuildRange.EXACT
    context.applicationInfo.isEAP || plugin.pluginCompatibilitySameRelease -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val snapshotSuffix = ".SNAPSHOT"
  val defaultPluginVersion = if (context.buildNumber.endsWith(snapshotSuffix)) {
    "${context.buildNumber.removeSuffix(snapshotSuffix)}.${pluginDateFormat.format(ZonedDateTime.now())}$snapshotSuffix"
  }
  else {
    context.buildNumber
  }

  val pluginVersion = plugin.versionEvaluator.evaluate(pluginXmlFile, defaultPluginVersion, context)
  val sinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber)
  @Suppress("TestOnlyProblems") val content = try {
    plugin.pluginXmlPatcher(
      // using input stream allows us to support BOM
      doPatchPluginXml(document = Files.newInputStream(pluginXmlFile).use { XMLParser().parse(XMLIOSource(it)) },
                       pluginModuleName = plugin.mainModule,
                       pluginVersion = pluginVersion,
                       releaseDate = releaseDate,
                       releaseVersion = releaseVersion,
                       compatibleSinceUntil = sinceUntil,
                       toPublish = pluginsToPublish.contains(plugin),
                       retainProductDescriptorForBundledPlugin = plugin.retainProductDescriptorForBundledPlugin,
                       isEap = context.applicationInfo.isEAP,
                       productName = context.applicationInfo.productName),
      context,
    )
  }
  catch (e: Throwable) {
    throw RuntimeException("Could not patch $pluginXmlFile", e)
  }
  moduleOutputPatcher.patchModuleOutput(plugin.mainModule, "META-INF/plugin.xml", content)
}

@TestOnly
fun doPatchPluginXml(document: Document,
                     pluginModuleName: String,
                     pluginVersion: String?,
                     releaseDate: String,
                     releaseVersion: String,
                     compatibleSinceUntil: Pair<String, String>,
                     toPublish: Boolean,
                     retainProductDescriptorForBundledPlugin: Boolean,
                     isEap: Boolean,
                     productName: String): String {
  val rootElement = document.rootElement
  val ideaVersionElement = getOrCreateTopElement(rootElement, "idea-version", listOf("id", "name"))
  ideaVersionElement.setAttribute("since-build", compatibleSinceUntil.first)
  ideaVersionElement.setAttribute("until-build", compatibleSinceUntil.second)
  val versionElement = getOrCreateTopElement(rootElement, "version", listOf("id", "name"))
  versionElement.text = pluginVersion
  val productDescriptor = rootElement.getChild("product-descriptor")
  if (productDescriptor != null) {
    if (!toPublish && !retainProductDescriptorForBundledPlugin) {
      Span.current().addEvent("skip $pluginModuleName <product-descriptor/>")
      removeTextBeforeElement(productDescriptor)
      productDescriptor.remove()
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
  return document.toXML()
}

fun getOrCreateTopElement(rootElement: Element, tagName: String, anchors: List<String>): Element {
  rootElement.getChild(tagName)?.let {
    return it
  }

  val newElement = Element(tagName)
  val anchor = anchors.asSequence().mapNotNull { rootElement.getChild(it) }.firstOrNull()
  if (anchor == null) {
    rootElement.addNode(0, newElement)
    rootElement.addNode(0, Text("\n  "))
  }
  else {
    val anchorIndex = rootElement.nodeIndexOf(anchor)
    // should not happen
    check(anchorIndex >= 0) {
      "anchor < 0 when getting child index of \'${anchor.name}\' in root element of ${rootElement.toXML()}"
    }
    var indent = rootElement.getNode(anchorIndex - 1)
    indent = if (indent is Text) indent.copy() else Text("")
    rootElement.addNode(anchorIndex + 1, newElement)
    rootElement.addNode(anchorIndex + 1, indent)
  }
  return newElement
}

private fun removeTextBeforeElement(element: Element) {
  val parentElement = element.parentElement ?: throw IllegalStateException("Could not find parent of \'${element.toXML()}\'")
  val elementIndex = parentElement.nodeIndexOf(element)
  check(elementIndex >= 0) { "Could not find element index \'${element.toXML()}\' in parent \'${parentElement.toXML()}\'" }
  if (elementIndex > 0) {
    val text = parentElement.getNode(elementIndex - 1)
    if (text is Text) {
      parentElement.removeNode(elementIndex - 1)
    }
  }
}

@Suppress("SameParameterValue")
private fun replaceInElementText(element: Element, oldText: String, newText: String): Boolean {
  var replaced = false
  for (node in element.nodes) {
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