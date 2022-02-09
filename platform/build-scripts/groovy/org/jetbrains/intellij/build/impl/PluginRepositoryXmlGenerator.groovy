// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.XmlDomReader
import com.intellij.util.XmlElement
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.xml.XmlUtil
import org.jetbrains.intellij.build.BuildContext

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class PluginRepositoryXmlGenerator {
  private PluginRepositoryXmlGenerator() {
  }

  static Path generate(List<PluginRepositorySpec> pluginSpecs, Path targetDir, BuildContext context) {
    TreeMap<String, List<Plugin>> categories = new TreeMap<String, List<Plugin>>()
    for (PluginRepositorySpec spec in pluginSpecs) {
      Plugin p = readPlugin(spec.pluginZip, spec.pluginXml, context.buildNumber, targetDir)
      categories.computeIfAbsent(p.category, { new ArrayList<>() }).add(p)
    }
    Path result = targetDir.resolve("plugins.xml")
    writePluginsXml(result, categories)
    return result
  }

  private static writePluginsXml(Path target, Map<String, List<Plugin>> categories) {
    Files.newBufferedWriter(target).withCloseable { out ->
      out.write("""<?xml version="1.0" encoding="UTF-8"?>\n""")
      out.write("""<plugin-repository>\n""")
      for (String it in categories.keySet()) {
        out.write("""  <category name="$it">\n""")
        for (Plugin p in categories.get(it).toSorted { o1, o2 -> NaturalComparator.INSTANCE.compare(o1.id, o2.id) }) {
          out.write("""\
    <idea-plugin size="$p.size">
      <name>${XmlUtil.escapeXml(p.name)}</name>
      <id>${XmlUtil.escapeXml(p.id ?: p.name)}</id>
      <version>$p.version</version>
      <idea-version since-build="$p.sinceBuild" until-build="$p.untilBuild"/>
      <vendor>${XmlUtil.escapeXml(p.vendor)}</vendor>
      <download-url>${XmlUtil.escapeXml(p.relativeFilePath)}</download-url>
      <description><![CDATA[$p.description]]></description>$p.depends
    </idea-plugin>\n""")
        }
        out.write("""  </category>\n""")
      }
      out.write("""</plugin-repository>\n""")
    }
  }

  private static Plugin readPlugin(Path pluginZip, byte[] pluginXml, String buildNumber, Path targetDirectory) {
    Plugin plugin
    try {
      XmlElement xml = XmlDomReader.readXmlAsModel(pluginXml)
      XmlElement versionNode = xml.getChild("idea-version")
      StringBuilder depends = new StringBuilder()
      xml.getChild("depends")?.children?.each {
        if (!it.attributes.containsKey("optional")) {
          depends.append("<depends>").append(it.content).append("</depends>")
        }
      }
      xml.getChild("dependencies")?.getChildren("plugin")?.each {
        depends.append("<depends>").append(it.getAttributeValue("id")).append("</depends>")
      }


      String name = xml.getChild("name")?.content
      plugin = new Plugin(
        id: xml.getChild("id")?.content ?: name,
        name: name,
        category: xml.getChild("category")?.content ?: "Misc",
        vendor: xml.getChild("vendor")?.content,
        sinceBuild: versionNode?.attributes?.get("since-build") ?: buildNumber,
        untilBuild: versionNode?.attributes?.get("until-build") ?: buildNumber,
        version: xml.getChild("version")?.content,
        description: xml.getChild("description")?.content,
        relativeFilePath: FileUtil.toSystemIndependentName(targetDirectory.relativize(pluginZip).toString()),
        size: Files.size(pluginZip),
        depends: depends.toString()
      )
    }
    catch (Throwable t) {
      throw new IllegalStateException("Unable to read: " + new String(pluginXml, StandardCharsets.UTF_8), t)
    }

    // todo this check never worked correctly because `new XmlParser().parse(pluginXml).description.text()` returns empty string if no value
    // the issue - xi:include is not handled
    // so, we exclude `"description"` for now
    for (String propertyName in ["id", "name", "vendor"]) {
      if (plugin.getProperty(propertyName) == null) {
        if (propertyName == "name" && (plugin.id.startsWith("com.intellij.appcode.") || plugin.id == "com.intellij.mobile.AppleGradlePluginGenerator")) {
          continue
        }
        throw new RuntimeException("Cannot generate plugin repository file: '$propertyName' " +
                                   "isn't specified in ${new String(pluginXml, StandardCharsets.UTF_8)}")
      }
    }

    return plugin
  }

  @Immutable
  private static final class Plugin {
    String id
    String name
    String vendor
    String version
    String sinceBuild
    String untilBuild
    String category
    String description
    long size
    String relativeFilePath
    String depends
  }
}

