package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import java.io.IOException
import kotlin.io.path.bufferedReader

internal data class PluginXmlContentData(
  val contentModuleNames: List<String>,
)

internal fun parsePluginXmlContent(descriptor: ResourceDescriptor): PluginXmlContentData? {
  val pluginXml = descriptor.root.resolve("META-INF/plugin.xml")
  val reader = try {
    pluginXml.bufferedReader()
  } catch (_: IOException) {
    //file not found
    return null
  }
  val firstLine = reader.readLine()
  if (!firstLine.startsWith(PLUGIN_XML_MARKER_PREFIX)) return null
  val root = JDOMUtil.load(reader)
  //xi:include tags aren't processed here; if they are present, ij_plugin rule will fail
  val contentModuleNames = root.getChildren("content").flatMap { contentTag ->
    contentTag.getChildren("module").mapNotNull { it.getAttributeValue("name") }
  }
  return PluginXmlContentData(contentModuleNames)
}

private const val PLUGIN_XML_MARKER_PREFIX = "<!-- BUILD_USING_BAZEL_MARKER"
