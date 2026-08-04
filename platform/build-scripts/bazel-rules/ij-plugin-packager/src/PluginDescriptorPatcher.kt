package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.openapi.util.JDOMUtil
import org.jdom.CDATA
import org.jdom.Element

/**
 * Includes bodies of content modules in the corresponding 'module' tags wrapped into CDATA.
 * @param pluginDescriptorRoot root tag of the plugin descriptor; it'll be modified in-place
 * @param contentModules maps from a name of a content module to text of its descriptor
 */
internal fun embedContentModules(pluginDescriptorRoot: Element, contentModules: Map<String, ByteArray>) {
  inlineXIncludes(pluginDescriptorRoot)
  for (contentElement in pluginDescriptorRoot.getChildren("content")) {
    for (moduleElement in contentElement.getChildren("module")) {
      val moduleName = requireNotNull(moduleElement.getAttributeValue("name")) { "'name' is required for 'module' tag in 'content' tag" }
      val contentDescriptor = contentModules[moduleName]
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
