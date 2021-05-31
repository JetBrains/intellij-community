// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
final class KotlinPluginArtifact {
  private final CompilationContext context

  KotlinPluginArtifact(CompilationContext context) {
    this.context = context
  }

  JpsArtifact build(KotlinPluginKind kind,
                    String sinceBuild, String untilBuild,
                    String version = kind.version) {
    def tasks = CompilationTasks.create(context)
    patchPluginXml(version, sinceBuild, untilBuild, kind)
    tasks.buildProjectArtifacts([kind.jpsArtifactIdentifier])
    return JpsArtifactService.instance.getArtifacts(context.project).find {
      it.name == kind.jpsArtifactIdentifier
    }
  }

  private static String replace(String oldText, String regex, String newText) {
    String result = oldText.replaceFirst(regex, newText)
    if (result == oldText && !oldText.contains(newText)) {
      throw new IllegalStateException("Cannot find '$regex' in '$oldText'")
    }
    return result
  }

  Path jpsOutPluginXml(KotlinPluginKind kind) {
    CompilationTasks.create(context).compileModules([kind.pluginXmlModuleName])
    def pluginXml = "production/${kind.pluginXmlModuleName}/META-INF/plugin.xml"
    Path jpsOutPluginXml = Paths.get("$context.projectOutputDirectory/$pluginXml")
    if (!Files.exists(jpsOutPluginXml)) {
      throw new IllegalStateException("jpsOutPluginXml=$jpsOutPluginXml doesn't exist!")
    }
    return jpsOutPluginXml
  }

  Path srcPluginXml(KotlinPluginKind kind) {
    Path srcPluginXml = Paths.get("$context.paths.projectHome/${kind.pluginXmlContentRoot}/META-INF/plugin.xml")
    if (!Files.exists(srcPluginXml)) {
      def pluginXmlContentRoot = kind.pluginXmlContentRoot
      if (pluginXmlContentRoot.startsWith('community/')) {
        pluginXmlContentRoot = kind.pluginXmlContentRoot.replaceFirst('community', '')
      }
      srcPluginXml = context.paths.communityHomeDir.resolve("$pluginXmlContentRoot/META-INF/plugin.xml")
    }
    if (!Files.exists(srcPluginXml)) {
      throw new IllegalStateException("srcPluginXml=$srcPluginXml doesn't exist!")
    }
    return srcPluginXml
  }

  private void patchPluginXml(String kotlinPluginVersion, String sinceBuild, String untilBuild, KotlinPluginKind kind) {
    def jpsOutPluginXml = jpsOutPluginXml(kind)

    switch (kind) {
      case KotlinPluginKind.AC:
      case KotlinPluginKind.AC_KMM:
        def extendedBuild = sinceBuild.substring(0, sinceBuild.lastIndexOf('.'))
        if (!sinceBuild.matches("\\d+\\.\\d+")) {
          sinceBuild = extendedBuild
        }
        untilBuild = extendedBuild + ".*"
        break
    }

    String versionText = "<version>${kotlinPluginVersion}</version>"

    String replaceText
    switch (kind) {
      case KotlinPluginKind.ROBOSCOPE:
        replaceText = versionText
        break
      default:
        replaceText = """
        ${versionText}
        <idea-version since-build="${sinceBuild}" until-build="${untilBuild}"/>
      """.stripIndent().trim()
        break
    }

    def newText = replace(Files.readString(jpsOutPluginXml), "<version>.*?</version>", replaceText)

    switch (kind) {
      case KotlinPluginKind.IJ:
      case KotlinPluginKind.IJ_CE:
        newText = replace(
          newText,
          "<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->",
          "<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>"
        )
        break
      case KotlinPluginKind.AS:
        newText = replace(
          newText,
          "<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->",
          "<depends>com.intellij.modules.androidstudio</depends>"
        )
        break
      case KotlinPluginKind.MI:
      case KotlinPluginKind.AC_KMM:
      case KotlinPluginKind.AC:
      case KotlinPluginKind.ROBOSCOPE:
        break
      default:
        throw new IllegalStateException("Unknown kind = $kind")
    }
    Files.writeString(jpsOutPluginXml, newText)
  }
}
