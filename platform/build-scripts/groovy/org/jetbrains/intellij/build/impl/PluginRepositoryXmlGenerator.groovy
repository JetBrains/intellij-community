// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl


import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.xml.XmlUtil
import org.jetbrains.intellij.build.BuildContext

/**
 * @author nik
 */
@CompileStatic
class PluginRepositoryXmlGenerator {
  private final BuildContext buildContext

  PluginRepositoryXmlGenerator(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void generate(List<PluginRepositorySpec> pluginSpecs, String targetDirectory) {
    def categories = new TreeMap<String, List<Plugin>>()
    pluginSpecs.each { spec ->
      def p = readPlugin(new File(spec.pluginZip), new File(spec.pluginXml), buildContext.buildNumber)
      categories.putIfAbsent(p.category, [])
      categories[p.category] << p
    }
    writePluginsXml(targetDirectory, categories)
  }

  private static writePluginsXml(String target, Map<String, List<Plugin>> categories) {
    new File(target, "plugins.xml").withWriter { out ->
      out.println """<?xml version="1.0" encoding="UTF-8"?>"""
      out.println """<plugin-repository>"""
      categories.keySet().each {
        out.println """  <category name="$it">"""
        categories.get(it).toSorted { o1, o2 -> StringUtil.naturalCompare(o1.id, o2.id)}.each { Plugin p ->
          out.println """\
    <idea-plugin size="$p.size">
      <name>${XmlUtil.escapeXml(p.name)}</name>
      <id>${XmlUtil.escapeXml(p.id ?: p.name)}</id>
      <version>$p.version</version>
      <idea-version since-build="$p.sinceBuild" until-build="$p.untilBuild"/>
      <vendor>${XmlUtil.escapeXml(p.vendor)}</vendor>
      <download-url>${XmlUtil.escapeXml(p.fileName)}</download-url>
      <description><![CDATA[$p.description]]></description>
    </idea-plugin>"""
        }
        out.println """  </category>"""
      }
      out.println """</plugin-repository>"""
    }
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  private Plugin readPlugin(File pluginZip, File pluginXml, String buildNumber) {
    def xml = new XmlParser().parse(pluginXml)
    def versionNode = xml."idea-version"[0]

    def plugin = new Plugin(
      id: xml.id.text(),
      name: xml.name.text(),
      category: xml.category.text() ?: "Misc",
      vendor: xml.vendor.text(),
      sinceBuild: versionNode?.attribute("since-build") ?: buildNumber,
      untilBuild: versionNode?.attribute("until-build") ?: buildNumber,
      version: buildNumber,
      description: xml.description.text(),
      fileName: pluginZip.name,
      size: pluginZip.length()
    )
    ["id", "name", "vendor", "description"].each {
      if (plugin.getProperty(it) == null) {
        buildContext.messages.error("Cannot generate plugin repository file: '$it' isn't specified in $pluginXml")
      }
    }
    return plugin
  }

  @Immutable
  private static class Plugin {
    String id
    String name
    String vendor
    String version
    String sinceBuild
    String untilBuild
    String category
    String description
    long size
    String fileName
  }
}

