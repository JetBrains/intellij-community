package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.openapi.util.JDOMUtil
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Namespace
import org.jetbrains.intellij.build.io.readEntryFromZip
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Computes the patched version of `plugin.xml` descriptor where `version` tag, `since-build` and `until-build` attributes are replaced by the provided values,
 * and bodies of content module descriptors are inlined in the corresponding 'module' tags wrapped into CDATA.
 * The `xi:include` elements of `plugin.xml` and of the inlined content module descriptors are replaced, see [inlineXIncludes].
 * The descriptor file in the JAR of a content module stays unchanged.
 * @param descriptorJar the JAR with `plugin.xml`
 * @param contentModules maps from a name of a content module to the text of its descriptor
 * @param skippedContentModuleNames names of content modules which are not included in the distribution; their 'module' tags are removed
 */
internal fun patchPluginDescriptor(
  originalContent: ByteArray,
  pluginVersion: String?,
  sinceBuild: String?,
  untilBuild: String?,
  contentModules: Map<String, ContentModuleData>,
  skippedContentModuleNames: Set<String>,
  descriptorJar: Path,
): ByteArray {
  val pluginDescriptorRoot = JDOMUtil.load(originalContent)
  inlineXIncludes(pluginDescriptorRoot, descriptorJar, PLUGIN_DESCRIPTOR_ENTRY_NAME)
  insertVersionAndCompatibilityRange(pluginDescriptorRoot, pluginVersion = pluginVersion, sinceBuild = sinceBuild, untilBuild = untilBuild)
  embedContentModules(pluginDescriptorRoot, contentModules, skippedContentModuleNames, descriptorJar.pathString)
  val patchedData = JDOMUtil.write(pluginDescriptorRoot)
  return patchedData.toByteArray()
}

private fun embedContentModules(
  pluginDescriptorRoot: Element,
  contentModules: Map<String, ContentModuleData>,
  skippedContentModuleNames: Set<String>,
  presentablePluginDescriptorLocation: String,
) {
  for (contentElement in pluginDescriptorRoot.getChildren("content")) {
    // copy the list, because skipped modules are removed from it
    for (moduleElement in contentElement.getChildren("module").toList()) {
      val moduleName = moduleElement.getAttributeValue("name")
                       ?: throw IjPluginPackagingException("Required 'name' attribute for 'module' tag in 'content' tag is missing in $presentablePluginDescriptorLocation")
      if (moduleName in skippedContentModuleNames) {
        moduleElement.detach()
        continue
      }
      val contentModule = contentModules.get(moduleName)
                          ?: throw IjPluginPackagingException("Descriptor for content module '$moduleName' is not found for plugin descriptor from $presentablePluginDescriptorLocation")

      val rootElement = contentModule.moduleDescriptorRoot
      if (!contentModule.shouldBeMergedToMainJar && rootElement.getAttributeValue("package") != null) {
        rootElement.setAttribute("separate-jar", "true")
      }
      inlineXIncludes(rootElement, contentModule.jars.first(), "$moduleName.xml")
      moduleElement.setContent(CDATA(JDOMUtil.write(rootElement)))
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

/**
 * Replaces each `xi:include` element in [rootElement] with the elements that it references.
 * [rootElement] is the root of the descriptor at [descriptorPath] in [jar]. The target files are searched in [jar] only.
 * An `href` that starts with `/` is resolved from the root of [jar]. Another `href` is resolved from the directory of the including file.
 * An `xi:include` element with a `fallback` child stays unchanged, as in the build scripts.
 * The only supported `xpointer` attribute value is [SUPPORTED_XPOINTER], which is also the default value.
 */
internal fun inlineXIncludes(rootElement: Element, jar: Path, descriptorPath: String) {
  XIncludeInliner(jar).inlineChildren(rootElement, descriptorPath, includeChain = listOf(descriptorPath))
}

private class XIncludeInliner(private val jar: Path) {
  /**
   * Replaces `xi:include` elements in the subtree of [element] from the file at [filePath].
   * [includeChain] lists the files from the root descriptor to [filePath].
   */
  fun inlineChildren(element: Element, filePath: String, includeChain: List<String>) {
    val content = element.content
    for (index in content.indices.reversed()) {
      val child = content.get(index) as? Element ?: continue
      if (!isXIncludeElement(child)) {
        inlineChildren(child, filePath, includeChain)
        continue
      }
      val includedElements = resolveXInclude(child, filePath, includeChain) ?: continue
      element.setContent(index, includedElements)
    }
  }

  /**
   * Returns the detached elements that [includeElement] references, or `null` if [includeElement] must stay unchanged.
   */
  private fun resolveXInclude(includeElement: Element, filePath: String, includeChain: List<String>): List<Element>? {
    if (includeElement.getChild("fallback", includeElement.namespace) != null) {
      // the IDE decides at runtime whether to include the file, so the build does not resolve it
      return null
    }

    val location = "$filePath in ${jar.pathString}"
    val href = includeElement.getAttributeValue("href")
               ?: throw IjPluginPackagingException("Required 'href' attribute for xi:include element is missing in $location")
    if (includeElement.getAttribute("base", Namespace.XML_NAMESPACE) != null) {
      throw IjPluginPackagingException("'xml:base' attribute for xi:include element is not supported in $location")
    }
    val xpointer = includeElement.getAttributeValue("xpointer")
    if (xpointer != null && xpointer != SUPPORTED_XPOINTER) {
      throw IjPluginPackagingException(
        "Unsupported 'xpointer' attribute value '$xpointer' for xi:include element in $location; only '$SUPPORTED_XPOINTER' is supported"
      )
    }
    val targetPath = resolveTargetPath(href, filePath)
    if (targetPath in includeChain) {
      throw IjPluginPackagingException("Circular xi:include of '$href' in $location: ${(includeChain + targetPath).joinToString(" -> ")}")
    }
    val targetContent = readEntryFromZip(jar, targetPath)
                        ?: throw IjPluginPackagingException(
                          "Cannot resolve xi:include of '$href' in $location: '$targetPath' is not found in the same JAR"
                        )
    val targetRoot = try {
      JDOMUtil.load(targetContent)
    }
    catch (e: JDOMException) {
      throw IjPluginPackagingException("Failed to parse '$targetPath' in ${jar.pathString} included from $filePath: ${e.message}")
    }

    if (targetRoot.name != "idea-plugin") {
      throw IjPluginPackagingException("Root element of '$targetPath' in ${jar.pathString} included from $filePath must be 'idea-plugin'")
    }
    inlineChildren(targetRoot, targetPath, includeChain + targetPath)
    return targetRoot.children.toList().onEach { it.detach() }
  }

  private fun resolveTargetPath(href: String, filePath: String): String {
    if (href.startsWith('/')) {
      return href.substring(1)
    }
    val directory = filePath.substringBeforeLast('/', missingDelimiterValue = "")
    return if (directory.isEmpty()) href else "$directory/$href"
  }
}

private const val SUPPORTED_XPOINTER = "xpointer(/idea-plugin/*)"

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
