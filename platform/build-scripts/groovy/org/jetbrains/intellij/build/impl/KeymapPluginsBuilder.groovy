// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.BuildContext

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class KeymapPluginsBuilder {
  static List<PluginRepositorySpec> buildKeymapPlugins(BuildContext buildContext, Path targetDir) {
    return List.of(
      keymapPlugin(["Mac OS X", "Mac OS X 10.5+"], buildContext, targetDir),
      keymapPlugin(["Default for GNOME"], buildContext, targetDir),
      keymapPlugin(["Default for KDE"], buildContext, targetDir),
      keymapPlugin(["Default for XWin"], buildContext, targetDir),
      keymapPlugin(["Eclipse", "Eclipse (Mac OS X)"], buildContext, targetDir),
      keymapPlugin(["Emacs"], buildContext, targetDir),
      keymapPlugin(["NetBeans 6.5"], buildContext, targetDir),
      keymapPlugin(["ReSharper", "ReSharper OSX"], buildContext, targetDir),
      keymapPlugin(["Sublime Text", "Sublime Text (Mac OS X)"], buildContext, targetDir),
      keymapPlugin(["Visual Studio", "Visual Studio OSX"], buildContext, targetDir),
      keymapPlugin(["Visual Assist", "Visual Assist OSX"], buildContext, targetDir),
      keymapPlugin(["VSCode", "VSCode OSX"], buildContext, targetDir),
      keymapPlugin(["Visual Studio for Mac"], buildContext, targetDir),
      keymapPlugin(["Xcode"], buildContext, targetDir)
    )
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  static PluginRepositorySpec keymapPlugin(List<String> keymaps, BuildContext buildContext, Path targetDir) {
    Path keymapPath = buildContext.paths.communityHomeDir.resolve("platform/platform-resources/src/keymaps")
    String longName = keymaps[0].replace("Mac OS X", "macOS").replaceAll("Default for |[.0-9]", "").trim()
    String shortName = longName.replaceAll(" ", "")
    Path tempDir = buildContext.paths.tempDir.resolve("keymap-plugins/${shortName.toLowerCase()}")
    Path metaInf = tempDir.resolve("META-INF")
    Files.createDirectories(metaInf)
    Files.writeString(metaInf.resolve("plugin.xml"), keymapPluginXml(buildContext.buildNumber, shortName.toLowerCase(), longName, keymaps))
    Path keymapDir = tempDir.resolve("keymaps")
    Files.createDirectories(keymapDir)
    for (name in keymaps) {
      String fileName = name + ".xml"
      Files.copy(keymapPath.resolve(name + ".xml"), keymapDir.resolve(fileName))
    }
    Files.writeString(metaInf.resolve("pluginIcon.svg"),
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\" viewBox=\"0 0 40 40\">\n" +
      "  <path fill=\"#389FD6\" fill-rule=\"evenodd\" d=\"M0,0 L36,0 L36,26 L0,26 L0,0 Z M16,5 L20,5 L20," +
      "9 L16,9 L16,5 Z M22,5 L26,5 L26,9 L22,9 L22,5 Z M28,5 L32,5 L32,9 L28,9 L28,5 Z M28,11 L32,11 L32," +
      "15 L28,15 L28,11 Z M22,11 L26,11 L26,15 L22,15 L22,11 Z M10,11 L14,11 L14,15 L10,15 L10,11 Z M4," +
      "11 L8,11 L8,15 L4,15 L4,11 Z M4,5 L8,5 L8,9 L4,9 L4,5 Z M10,5 L14,5 L14,9 L10,9 L10,5 Z M16,11 L20," +
      "11 L20,15 L16,15 L16,11 Z M25,21 L11,21 L11,17 L25,17 L25,21 Z\" transform=\"translate(2 7)\"/>\n" +
      "</svg>\n")
    Files.writeString(metaInf.resolve("pluginIcon_dark.svg"),
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\" viewBox=\"0 0 40 40\">\n" +
      "  <path fill=\"#3592C4\" fill-rule=\"evenodd\" d=\"M0,0 L36,0 L36,26 L0,26 L0,0 Z M16,5 L20,5 L20," +
      "9 L16,9 L16,5 Z M22,5 L26,5 L26,9 L22,9 L22,5 Z M28,5 L32,5 L32,9 L28,9 L28,5 Z M28,11 L32,11 L32," +
      "15 L28,15 L28,11 Z M22,11 L26,11 L26,15 L22,15 L22,11 Z M10,11 L14,11 L14,15 L10,15 L10,11 Z M4," +
      "11 L8,11 L8,15 L4,15 L4,11 Z M4,5 L8,5 L8,9 L4,9 L4,5 Z M10,5 L14,5 L14,9 L10,9 L10,5 Z M16,11 L20," +
      "11 L20,15 L16,15 L16,11 Z M25,21 L11,21 L11,17 L25,17 L25,21 Z\" transform=\"translate(2 7)\"/>\n" +
      "</svg>\n")
    new LayoutBuilder(buildContext, true).layout(targetDir.toString()) {
      zip("${shortName}Keymap.zip") {
        jar("${shortName}Keymap/lib/${shortName}Keymap.jar") {
          ant.fileset(dir: "$tempDir")
        }
      }
    }
    Path resultFile = targetDir.resolve("${shortName}Keymap.zip")
    buildContext.notifyArtifactWasBuilt(resultFile)
    return new PluginRepositorySpec(pluginZip: resultFile.toString(),
                                    pluginXml: metaInf.resolve("plugin.xml").toString())
  }

  static String keymapPluginXml(String version, String id, String name, List<String> keymaps) {
    return """<idea-plugin>
  <name>$name Keymap</name>
  <id>com.intellij.plugins.${id}keymap</id>
  <version>$version</version>
  <idea-version since-build="${version.substring(0, version.lastIndexOf('.'))}"/>
  <vendor>JetBrains</vendor>
  <category>Keymap</category>
  <description>
    $name keymap for all IntelliJ-based IDEs.
    Use this plugin if $name keymap is not pre-installed in your IDE.
  </description>
  <depends>com.intellij.modules.lang</depends>
  <extensions defaultExtensionNs="com.intellij">
${
      keymaps.collect {
        "    <bundledKeymap file=\"${it}.xml\"/>"
      }
        .join("\n")
    }
  </extensions>
</idea-plugin>"""
  }
}