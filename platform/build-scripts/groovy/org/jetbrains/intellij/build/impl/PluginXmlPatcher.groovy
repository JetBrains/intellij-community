// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import de.pdark.decentxml.*
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompatibleBuildRange

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@CompileStatic
final class PluginXmlPatcher {
  static final DateTimeFormatter pluginDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

  private final String myReleaseDate
  private final String myReleaseVersion

  PluginXmlPatcher(String releaseDate, String releaseVersion) {
    myReleaseDate = releaseDate
    myReleaseVersion = releaseVersion
  }

  static Pair<String, String> getCompatiblePlatformVersionRange(CompatibleBuildRange compatibleBuildRange, String buildNumber) {
    String sinceBuild
    String untilBuild
    if (compatibleBuildRange != CompatibleBuildRange.EXACT && buildNumber.matches(/(\d+\.)+\d+/)) {
      if (compatibleBuildRange == CompatibleBuildRange.ANY_WITH_SAME_BASELINE) {
        sinceBuild = buildNumber.substring(0, buildNumber.indexOf('.'))
        untilBuild = buildNumber.substring(0, buildNumber.indexOf('.')) + ".*"
      }
      else {
        if (buildNumber.matches(/\d+\.\d+/)) {
          sinceBuild = buildNumber
        }
        else {
          sinceBuild = buildNumber.substring(0, buildNumber.lastIndexOf('.'))
        }
        int end = compatibleBuildRange == CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE ? buildNumber.lastIndexOf('.') : buildNumber.indexOf('.')
        untilBuild = buildNumber.substring(0, end) + ".*"
      }
    }
    else {
      sinceBuild = buildNumber
      untilBuild = buildNumber
    }
    return new Pair<>(sinceBuild, untilBuild)
  }

  static void patchPluginXml(ModuleOutputPatcher moduleOutputPatcher,
                               PluginLayout plugin,
                               Set<PluginLayout> pluginsToPublish,
                               PluginXmlPatcher pluginXmlPatcher,
                               BuildContext context) {
    boolean bundled = !pluginsToPublish.contains(plugin)
    Path moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.mainModule))
    Path pluginXmlPath = moduleOutput.resolve("META-INF/plugin.xml")
    if (Files.notExists(pluginXmlPath)) {
      context.messages.error("plugin.xml not found in $plugin.mainModule module: $pluginXmlPath")
    }

    def productLayout = context.productProperties.productLayout
    def includeInBuiltinCustomRepository = productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                           context.proprietaryBuildTools.artifactsServer != null
    CompatibleBuildRange compatibleBuildRange = bundled || plugin.pluginCompatibilityExactVersion ||
                                                //plugins included into the built-in custom plugin repository should use EXACT range because such custom repositories are used for nightly builds and there may be API differences between different builds
                                                includeInBuiltinCustomRepository ? CompatibleBuildRange.EXACT :
      //when publishing plugins with EAP build let's use restricted range to ensure that users will update to a newer version of the plugin when they update to the next EAP or release build
                                                context.applicationInfo.isEAP ? CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
                                                                                   : CompatibleBuildRange.NEWER_WITH_SAME_BASELINE

    String defaultPluginVersion = context.buildNumber.endsWith(".SNAPSHOT")
      ? context.buildNumber + ".${pluginDateFormat.format(ZonedDateTime.now())}"
      : context.buildNumber

    String pluginVersion = plugin.versionEvaluator.evaluate(pluginXmlPath, defaultPluginVersion, context)

    Pair<String, String> sinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, context.buildNumber)
    String content
    try {
      content = pluginXmlPatcher.patchPluginXml(
        pluginXmlPath,
        plugin.mainModule,
        pluginVersion,
        sinceUntil,
        pluginsToPublish.contains(plugin),
        plugin.retainProductDescriptorForBundledPlugin,
        context.applicationInfo.isEAP,
        context.applicationInfo.productName
        )
      content = plugin.pluginXmlPatcher.apply(content)
    }
    catch (Throwable e) {
      throw new RuntimeException("Could not patch $pluginXmlPath", e)
    }

    moduleOutputPatcher.patchModuleOutput(plugin.mainModule, "META-INF/plugin.xml", content)
  }

  String patchPluginXml(@NotNull Path pluginXmlFile,
                        String pluginModuleName,
                        String pluginVersion,
                        Pair<String, String> compatibleSinceUntil,
                        boolean toPublish,
                        boolean retainProductDescriptorForBundledPlugin,
                        boolean isEap,
                        String productName) {
    Document doc = XMLParser.parse(Files.readString(pluginXmlFile))

    def ideaVersionElement = getOrCreateTopElement(doc, "idea-version", ["id", "name"])
    ideaVersionElement.setAttribute("since-build", compatibleSinceUntil.first)
    ideaVersionElement.setAttribute("until-build", compatibleSinceUntil.second)

    def versionElement = getOrCreateTopElement(doc, "version", ["id", "name"])
    versionElement.setText(pluginVersion)

    def productDescriptor = doc.rootElement.getChild("product-descriptor")
    if (productDescriptor != null) {
      Span.current().addEvent("${toPublish ? "patch" : "skip"} $pluginModuleName <product-descriptor/>")

      setProductDescriptorEapAttribute(productDescriptor, isEap)

      productDescriptor.setAttribute("release-date", myReleaseDate)
      productDescriptor.setAttribute("release-version", myReleaseVersion)

      if (!toPublish && !retainProductDescriptorForBundledPlugin) {
        removeTextBeforeElement(productDescriptor)
        productDescriptor.remove()
      }
    }

    // Patch Database plugin for WebStorm, see WEB-48278
    if (toPublish && productDescriptor != null && productDescriptor.getAttributeValue("code") == "PDB" && productName == "WebStorm") {
      Span.current().addEvent("patch $pluginModuleName for WebStorm")

      def pluginName = doc.rootElement.getChild("name")
      if (pluginName.getText() != "Database Tools and SQL") {
        throw new IllegalStateException("Plugin name for '$pluginModuleName' should be 'Database Tools and SQL'")
      }
      pluginName.setText("Database Tools and SQL for WebStorm")

      def description = doc.rootElement.getChild("description")
      def replaced = replaceInElementText(description, "IntelliJ-based IDEs", "WebStorm")
      if (!replaced) {
        throw new IllegalStateException("Could not find 'IntelliJ-based IDEs' in plugin description of $pluginModuleName")
      }
    }

    return doc.toXML()
  }

  private static void removeTextBeforeElement(Element element) {
    def parentElement = element.parentElement
    if (parentElement == null) {
      throw new IllegalStateException("Could not find parent of '${element.toXML()}'")
    }

    def elementIndex = parentElement.nodeIndexOf(element)
    if (elementIndex < 0) {
      throw new IllegalStateException("Could not find element index '${element.toXML()}' in parent '${parentElement.toXML()}'")
    }

    if (elementIndex > 0) {
      def text = parentElement.getNode(elementIndex - 1)
      if (text instanceof Text) {
        parentElement.removeNode(elementIndex - 1)
      }
    }
  }

  private static Element getOrCreateTopElement(Document doc, String tagName, List<String> anchors) {
    def child = doc.rootElement.getChild(tagName)
    if (child != null) {
      return child
    }

    def newElement = new Element(tagName)

    def anchor = anchors.collect { doc.rootElement.getChild(it) }.find { it != null }
    if (anchor == null) {
      doc.rootElement.addNode(0, newElement)
      doc.rootElement.addNode(0, new Text("\n  "))
    }
    else {
      def anchorIndex = doc.rootElement.nodeIndexOf(anchor)
      if (anchorIndex < 0) {
        // Should not happen
        throw new IllegalStateException("anchor < 0 when getting child index of '${anchor.name}' in root element of ${doc.toXML()}")
      }

      def indent = doc.rootElement.getNode(anchorIndex - 1)
      if (indent instanceof Text) {
        indent = indent.copy()
      }
      else {
        indent = new Text("")
      }

      doc.rootElement.addNode(anchorIndex + 1, newElement)
      doc.rootElement.addNode(anchorIndex + 1, indent)
    }

    return newElement
  }

  private static void setProductDescriptorEapAttribute(Element productDescriptor, boolean isEap) {
    if (isEap) {
      productDescriptor.setAttribute("eap", "true")
    }
    else {
      productDescriptor.removeAttribute("eap")
    }
  }

  private static boolean replaceInElementText(Element element, String oldText, String newText) {
    boolean replaced = false
    for (Node node : element.getNodes()) {
      if (node instanceof Text) {
        def textBefore = node.text
        def text = textBefore.replace(oldText, newText)
        if (textBefore != text) {
          replaced = true
          node.setText(text)
        }
      }
    }

    return replaced
  }
}
