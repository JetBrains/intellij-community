package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.bufferedReader

internal data class PluginXmlContentData(
  @JvmField val contentModuleNames: List<String>,
)

internal fun findMarkedPluginXmlFile(descriptor: ResourceDescriptor): Path? {
  val pluginXml = descriptor.root.resolve("META-INF/plugin.xml")
  val firstLine = try {
    pluginXml.bufferedReader().use { it.readLine() }
  } catch (_: IOException) {
    // file not found
    return null
  }
  return pluginXml.takeIf { firstLine?.startsWith(PLUGIN_XML_MARKER_PREFIX) == true }
}

internal fun parsePluginXmlContent(descriptor: ResourceDescriptor): PluginXmlContentData? {
  val pluginXml = findMarkedPluginXmlFile(descriptor) ?: return null
  val root = pluginXml.bufferedReader().use { reader ->
    JDOMUtil.load(reader)
  }
  //xi:include tags aren't processed here; if they are present, ij_plugin rule will fail
  val contentModuleNames = root.getChildren("content").flatMap { contentTag ->
    contentTag.getChildren("module").mapNotNull { it.getAttributeValue("name") }
  }
  return PluginXmlContentData(contentModuleNames)
}

private const val PLUGIN_XML_MARKER_PREFIX = "<!-- BUILD_USING_BAZEL_MARKER"
