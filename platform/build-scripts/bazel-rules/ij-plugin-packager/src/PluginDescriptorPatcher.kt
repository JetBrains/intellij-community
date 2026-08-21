package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.openapi.util.JDOMUtil
import org.jdom.CDATA
import org.jdom.Element

/**
 * Computes the patched version of `plugin.xml` descriptor where `version` tag, `since-build` and `until-build` attributes are replaced by the provided values,
 * and bodies of content module descriptors are inlined in the corresponding 'module' tags wrapped into CDATA.
 * @param contentModuleDescriptors maps from a name of a content module to the text of its descriptor
 */
internal fun patchPluginDescriptor(
  originalContent: ByteArray,
  pluginVersion: String?,
  sinceBuild: String?,
  untilBuild: String?,
  contentModuleDescriptors: Map<String, ByteArray>,
): ByteArray {
  val pluginDescriptorRoot = JDOMUtil.load(originalContent)
  insertVersionAndCompatibilityRange(pluginDescriptorRoot, pluginVersion = pluginVersion, sinceBuild = sinceBuild, untilBuild = untilBuild)
  embedContentModules(pluginDescriptorRoot, contentModuleDescriptors)
  val patchedData = JDOMUtil.write(pluginDescriptorRoot)
  return patchedData.toByteArray()
}

private fun embedContentModules(pluginDescriptorRoot: Element, contentModuleDescriptors: Map<String, ByteArray>) {
  inlineXIncludes(pluginDescriptorRoot)
  for (contentElement in pluginDescriptorRoot.getChildren("content")) {
    for (moduleElement in contentElement.getChildren("module")) {
      val moduleName = requireNotNull(moduleElement.getAttributeValue("name")) { "'name' is required for 'module' tag in 'content' tag" }
      val contentDescriptor = contentModuleDescriptors[moduleName]
      requireNotNull(contentDescriptor) { "Descriptor for content module '$moduleName' is not found" }
      val contentDescriptorRoot = JDOMUtil.load(contentDescriptor)
      inlineXIncludes(contentDescriptorRoot)
      moduleElement.setContent(CDATA(JDOMUtil.write(contentDescriptorRoot)))
    }
  }

  // restore wrapping into CDATA for multi-line texts
  for (name in arrayOf("description", "change-notes")) {
    pluginDescriptorRoot.getChild(name)?.let {
      val text = it.text
      if (text.isNotEmpty()) {
        it.setContent(CDATA(text))
      }
    }
  }
}

private fun inlineXIncludes(rootElement: Element) {
  //todo support inlining at least for target files in the same module or its direct dependencies
  for (element in rootElement.children) {
    if (isXIncludeElement(element)) {
      error("xi:include elements are not supported yet")
    }
  }
}

private const val XI_INCLUDE_URI = "http://www.w3.org/2001/XInclude"

private fun isXIncludeElement(element: Element): Boolean {
  return element.name == "include" && element.namespace?.uri == XI_INCLUDE_URI
}

private fun insertVersionAndCompatibilityRange(rootElement: Element, pluginVersion: String?, sinceBuild: String?, untilBuild: String?) {
  if (sinceBuild != null || untilBuild != null) {
    val versionElement = getOrCreateTag(rootElement, "idea-version")
    if (sinceBuild != null) {
      versionElement.setAttribute("since-build", sinceBuild)
    }
    if (untilBuild != null) {
      versionElement.setAttribute("until-build", untilBuild)
    }
  }
  if (pluginVersion != null) {
    getOrCreateTag(rootElement, "version").text = pluginVersion
  }
}

private fun getOrCreateTag(rootElement: Element, tagName: String): Element {
  val existing = rootElement.getChild(tagName)
  if (existing != null) return existing

  val newElement = Element(tagName)
  val anchor = rootElement.getChild("id") ?: rootElement.getChild("name")
  val index = if (anchor != null) rootElement.indexOf(anchor) + 1 else 0
  rootElement.addContent(index, newElement)
  return newElement
}
