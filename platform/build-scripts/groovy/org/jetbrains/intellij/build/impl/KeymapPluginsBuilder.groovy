// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl


import org.jetbrains.intellij.build.BuildContext

class KeymapPluginsBuilder {
  final BuildContext buildContext
  final String targetDir

  KeymapPluginsBuilder(BuildContext buildContext, String targetDir) {
    this.targetDir = targetDir
    this.buildContext = buildContext
  }

  static void buildKeymapPlugins(BuildContext buildContext, String targetDir) {
    def builder = new KeymapPluginsBuilder(buildContext, targetDir)
    [["Default for GNOME"],
     ["Default for KDE"],
     ["Default for XWin"],
     ["Eclipse", "Eclipse (Mac OS X)"],
     ["Emacs"],
     ["NetBeans 6.5"],
     ["ReSharper", "ReSharper OSX"],
     ["Sublime Text", "Sublime Text (Mac OS X)"],
     ["Visual Studio"],
     ["Xcode"]].forEach {
      builder.keymapPlugin(targetDir, it)
    }
  }

  void keymapPlugin(String targetDir, List<String> keymaps) {
    def keymapPath = "$buildContext.paths.communityHome/platform/platform-resources/src/keymaps"
    def longName = keymaps[0].replaceAll("Default for ", "")
    def shortName = keymaps[0].replaceAll("[.0-9 ]|Default for ", "")
    def tempDir = new File(buildContext.paths.temp, "keymap-plugins/${shortName.toLowerCase()}")
    new File(tempDir, "META-INF").mkdirs()
    new File(tempDir, "META-INF/plugin.xml").text = keymapPluginXml(buildContext.buildNumber, shortName.toLowerCase(), longName, keymaps)
    keymaps.forEach {
      buildContext.ant.copy(file: "$keymapPath/${it}.xml", todir: "$tempDir/keymaps")
    }
    new File(tempDir, "META-INF/pluginIcon.svg").text =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\" viewBox=\"0 0 40 40\">\n" +
      "  <path fill=\"#389FD6\" fill-rule=\"evenodd\" d=\"M0,0 L36,0 L36,26 L0,26 L0,0 Z M16,5 L20,5 L20," +
      "9 L16,9 L16,5 Z M22,5 L26,5 L26,9 L22,9 L22,5 Z M28,5 L32,5 L32,9 L28,9 L28,5 Z M28,11 L32,11 L32," +
      "15 L28,15 L28,11 Z M22,11 L26,11 L26,15 L22,15 L22,11 Z M10,11 L14,11 L14,15 L10,15 L10,11 Z M4," +
      "11 L8,11 L8,15 L4,15 L4,11 Z M4,5 L8,5 L8,9 L4,9 L4,5 Z M10,5 L14,5 L14,9 L10,9 L10,5 Z M16,11 L20," +
      "11 L20,15 L16,15 L16,11 Z M25,21 L11,21 L11,17 L25,17 L25,21 Z\" transform=\"translate(2 7)\"/>\n" +
      "</svg>\n"
    new File(tempDir, "META-INF/pluginIcon_dark.svg").text =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\" viewBox=\"0 0 40 40\">\n" +
      "  <path fill=\"#3592C4\" fill-rule=\"evenodd\" d=\"M0,0 L36,0 L36,26 L0,26 L0,0 Z M16,5 L20,5 L20," +
      "9 L16,9 L16,5 Z M22,5 L26,5 L26,9 L22,9 L22,5 Z M28,5 L32,5 L32,9 L28,9 L28,5 Z M28,11 L32,11 L32," +
      "15 L28,15 L28,11 Z M22,11 L26,11 L26,15 L22,15 L22,11 Z M10,11 L14,11 L14,15 L10,15 L10,11 Z M4," +
      "11 L8,11 L8,15 L4,15 L4,11 Z M4,5 L8,5 L8,9 L4,9 L4,5 Z M10,5 L14,5 L14,9 L10,9 L10,5 Z M16,11 L20," +
      "11 L20,15 L16,15 L16,11 Z M25,21 L11,21 L11,17 L25,17 L25,21 Z\" transform=\"translate(2 7)\"/>\n" +
      "</svg>\n"
    new LayoutBuilder(buildContext, true).layout(targetDir) {
      zip("${shortName}Keymap.zip") {
        jar("${shortName}Keymap/lib/${shortName}Keymap.jar") {
          ant.fileset(dir: "$tempDir")
        }
      }
    }
    buildContext.notifyArtifactBuilt("$targetDir/${shortName}Keymap.zip")
  }

  static String keymapPluginXml(String version, String id, String name, List<String> keymaps) {
    return """<idea-plugin>
  <name>$name Keymap</name>
  <id>com.intellij.plugins.${id}keymap</id>
  <idea-version since-build="${version.substring(0, version.lastIndexOf('.'))}"/>
  <version>$version</version>
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