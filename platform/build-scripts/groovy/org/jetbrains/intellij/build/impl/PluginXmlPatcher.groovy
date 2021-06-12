// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import de.pdark.decentxml.Document
import de.pdark.decentxml.Element
import de.pdark.decentxml.Node
import de.pdark.decentxml.Text
import de.pdark.decentxml.XMLParser
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class PluginXmlPatcher {
  private final BuildMessages myBuildMessages
  private final String myReleaseDate
  private final String myReleaseVersion
  private final String myProductName
  private final boolean myIsEap

  PluginXmlPatcher(BuildMessages buildMessages, String releaseDate, String releaseVersion, String productName, boolean isEap) {
    myBuildMessages = buildMessages
    myReleaseDate = releaseDate
    myReleaseVersion = releaseVersion
    myIsEap = isEap
    myProductName = productName
  }

  void patchPluginXml(@NotNull Path pluginXmlFile,
                      String pluginModuleName,
                      String pluginVersion,
                      Pair<String, String> compatibleSinceUntil,
                      boolean toPublish,
                      boolean retainProductDescriptorForBundledPlugin) {
    Document doc = XMLParser.parse(Files.readString(pluginXmlFile))

    def ideaVersionElement = getOrCreateTopElement(doc, "idea-version", ["id", "name"])
    ideaVersionElement.setAttribute("since-build", compatibleSinceUntil.first)
    ideaVersionElement.setAttribute("until-build", compatibleSinceUntil.second)

    def versionElement = getOrCreateTopElement(doc, "version", ["id", "name"])
    versionElement.setText(pluginVersion)

    def productDescriptor = doc.rootElement.getChild("product-descriptor")
    if (productDescriptor != null) {
      myBuildMessages.info("        ${toPublish ? "Patching" : "Skipping"} $pluginModuleName <product-descriptor/>")

      setProductDescriptorEapAttribute(productDescriptor, myIsEap)

      productDescriptor.setAttribute("release-date", myReleaseDate)
      productDescriptor.setAttribute("release-version", myReleaseVersion)

      if (!toPublish && !retainProductDescriptorForBundledPlugin) {
        removeTextBeforeElement(productDescriptor)
        productDescriptor.remove()
      }
    }

    // Patch Database plugin for WebStorm, see WEB-48278
    if (toPublish && productDescriptor != null && productDescriptor.getAttributeValue("code") == "PDB" && myProductName == "WebStorm") {
      myBuildMessages.info("        Patching $pluginModuleName for WebStorm")

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

    Files.writeString(pluginXmlFile, doc.toXML())
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
