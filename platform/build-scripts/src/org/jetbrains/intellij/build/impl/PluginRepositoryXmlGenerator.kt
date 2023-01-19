// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.xml.dom.readXmlAsModel
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.intellij.build.BuildContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal fun generatePluginRepositoryMetaFile(pluginSpecs: List<PluginRepositorySpec>, targetDir: Path, context: BuildContext): Path {
  val categories = TreeMap<String, MutableList<Plugin>>()
  for (spec in pluginSpecs) {
    val p = readPlugin(spec.pluginZip, spec.pluginXml, context.buildNumber, targetDir)
    categories.computeIfAbsent(p.category) { mutableListOf() }.add(p)
  }
  val result = targetDir.resolve("plugins.xml")
  writePluginsXml(result, categories)
  return result
}

private fun writePluginsXml(target: Path, categories: Map<String, List<Plugin>>) {
  Files.newBufferedWriter(target).use { out ->
    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.write("<plugin-repository>\n")
    for (it in categories.keys) {
      out.write("  <category name=\"$it\">\n")
      for (p in categories.get(it)!!.sortedWith { o1, o2 -> NaturalComparator.INSTANCE.compare(o1.id, o2.id) }) {
        out.write("""
          <idea-plugin size="${p.size}">
            <name>${XmlStringUtil.escapeString(p.name)}</name>
            <id>${XmlStringUtil.escapeString(p.id ?: p.name)}</id>
            <version>${p.version}</version>
            <idea-version since-build="${p.sinceBuild}" until-build="${p.untilBuild}"/>
            <vendor>${XmlStringUtil.escapeString(p.vendor)}</vendor>
            <download-url>${XmlStringUtil.escapeString(p.relativeFilePath)}</download-url>
            ${p.description?.let { "<description>${XmlStringUtil.wrapInCDATA(p.description)}</description>" }}
            ${p.depends}
          </idea-plugin>
        """.trimIndent())
      }
      out.write("  </category>\n")
    }
    out.write("</plugin-repository>\n")
  }
}

private fun readPlugin(pluginZip: Path, pluginXml: ByteArray, buildNumber: String, targetDirectory: Path): Plugin {
  val plugin = try {
    val xml = readXmlAsModel(pluginXml)
    val versionNode = xml.getChild("idea-version")
    val depends = StringBuilder()
    xml.getChild("depends")?.children?.forEach {
      if (!it.attributes.containsKey("optional")) {
        depends.append("<depends>").append(it.content).append("</depends>")
      }
    }
    xml.getChild("dependencies")?.children("plugin")?.iterator()?.forEach {
      depends.append("<depends>").append(it.getAttributeValue("id")).append("</depends>")
    }

    val name = xml.getChild("name")?.content
    Plugin(
      id = xml.getChild("id")?.content ?: name,
      name = name,
      category = xml.getChild("category")?.content ?: "Misc",
      vendor = xml.getChild("vendor")?.content,
      sinceBuild = versionNode?.attributes?.get("since-build") ?: buildNumber,
      untilBuild = versionNode?.attributes?.get("until-build") ?: buildNumber,
      version = xml.getChild("version")?.content,
      description = xml.getChild("description")?.content,
      relativeFilePath = FileUtilRt.toSystemIndependentName(targetDirectory.relativize(pluginZip).toString()),
      size = Files.size(pluginZip),
      depends = depends.toString()
    )
  }
  catch (t: Throwable) {
    throw IllegalStateException("Unable to read: ${pluginXml.decodeToString()}", t)
  }

  // todo this check never worked correctly because `new XmlParser().parse(pluginXml).description.text()` returns empty string if no value
  // the issue - xi:include is not handled
  // so, we exclude `"description"` for now
  sequenceOf("id" to plugin.id, "name" to plugin.name, "vendor" to plugin.vendor)
    .filter { it.second == null }
    .map { it.first }
    .forEach { propertyName ->
      if (propertyName == "name" &&
          (plugin.id!!.startsWith("com.intellij.appcode.") || plugin.id == "com.intellij.mobile.AppleGradlePluginGenerator")) {
        return@forEach
      }
      throw RuntimeException("Cannot generate plugin repository file: '$propertyName' isn't specified in ${pluginXml.decodeToString()}")
    }

  return plugin
}

private data class Plugin(
  @JvmField val id: String?,
  @JvmField val name: String?,
  @JvmField val vendor: String?,
  @JvmField val version: String?,
  @JvmField val sinceBuild: String,
  @JvmField val untilBuild: String,
  @JvmField val category: String,
  @JvmField val description: String?,
  @JvmField val size: Long,
  @JvmField val relativeFilePath: String?,
  @JvmField val depends: String?,
)

