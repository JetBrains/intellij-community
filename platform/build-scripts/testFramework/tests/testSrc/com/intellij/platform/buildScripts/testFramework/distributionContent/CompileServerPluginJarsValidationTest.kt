// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.PluginContentReport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** [collectCompileServerPluginJarFailures] over hand-built reports and descriptors. */
class CompileServerPluginJarsValidationTest {
  private val groovy = "intellij.groovy"

  private val groovyPluginXml = """
    <idea-plugin>
      <id>org.intellij.groovy</id>
      <extensions defaultExtensionNs="com.intellij">
        <compileServer.plugin classpath="groovy-jps.jar;groovy-constants-rt.jar"/>
      </extensions>
    </idea-plugin>
  """.trimIndent()

  private fun module(name: String): ModuleEntry = ModuleEntry(name = name)

  private fun collect(report: PluginContentReport, descriptors: Map<String, String>, ignoredPlugins: Map<String, String> = emptyMap()) =
    collectCompileServerPluginJarFailures(
      reports = listOf(report),
      readDescriptors = { moduleName, _ -> listOfNotNull(descriptors[moduleName]?.toByteArray()) },
      ignoredPlugins = ignoredPlugins,
    )

  @Test
  fun `a plugin that contains the named jars reports nothing`() {
    val report = PluginContentReport(mainModule = groovy, content = listOf(
      FileEntry(name = "lib/Groovy.jar", modules = listOf(module(groovy))),
      FileEntry(name = "lib/groovy-jps.jar", modules = listOf(module("intellij.groovy.jps"))),
      FileEntry(name = "lib/groovy-constants-rt.jar", modules = listOf(module("intellij.groovy.constants.rt"))),
    ))
    assertThat(collect(report, mapOf(groovy to groovyPluginXml))).isEmpty()
  }

  // IDEA-395490: RustRover packed Groovy with the default layout, which merges the JPS module into the main jar.
  @Test
  fun `a plugin packed with the default layout reports the missing jars`() {
    val report = PluginContentReport(mainModule = groovy, content = listOf(
      FileEntry(name = "lib/groovy.jar", modules = listOf(module(groovy), module("intellij.groovy.jps"))),
    ))
    (val name, val error) = collect(report, mapOf(groovy to groovyPluginXml)).single()
    assertThat(name).isEqualTo("compile-server-plugin-jars: $groovy")
    assertThat(error.message)
      .contains("groovy-jps.jar (declared in $groovy)")
      .contains("groovy-constants-rt.jar (declared in $groovy)")
  }

  @Test
  fun `a content module descriptor is read and a subdirectory path is resolved under lib`() {
    val maven = "intellij.maven"
    val report = PluginContentReport(mainModule = maven, content = listOf(
      FileEntry(name = "lib/maven.jar", modules = listOf(module(maven))),
      FileEntry(name = "lib/modules/intellij.maven.jps.jar", contentModules = listOf(module("intellij.maven.jps"))),
    ))
    val descriptor = """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <compileServer.plugin classpath="modules/intellij.maven.jps.jar"/>
        </extensions>
      </idea-plugin>
    """.trimIndent()
    assertThat(collect(report, mapOf(maven to descriptor))).isEmpty()
    assertThat(collect(report.copy(content = report.content.take(1)), mapOf(maven to descriptor)).single().error.message)
      .contains("modules/intellij.maven.jps.jar (declared in $maven)")
  }

  // A packed IDE resolves only `lib/<path>`. The file name fallback works only when the IDE runs from sources.
  @Test
  fun `a jar found only by its file name directly under lib is reported`() {
    val report = PluginContentReport(mainModule = "intellij.sample", content = listOf(
      FileEntry(name = "lib/sample-jps.jar", modules = listOf(module("intellij.sample.jps"))),
    ))
    val descriptor = """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <compileServer.plugin classpath="jps/sample-jps.jar"/>
        </extensions>
      </idea-plugin>
    """.trimIndent()
    assertThat(collect(report, mapOf("intellij.sample.jps" to descriptor)).single().error.message)
      .contains("jps/sample-jps.jar (declared in intellij.sample.jps)")
  }

  // The IDE does not trim the classpath entries, so it looks for `lib/ sample-rt.jar`.
  @Test
  fun `a classpath entry with a space is reported`() {
    val report = PluginContentReport(mainModule = "intellij.sample", content = listOf(
      FileEntry(name = "lib/sample-jps.jar", modules = listOf(module("intellij.sample"))),
      FileEntry(name = "lib/sample-rt.jar", modules = listOf(module("intellij.sample.rt"))),
    ))
    val descriptor = """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <compileServer.plugin classpath="sample-jps.jar; sample-rt.jar"/>
        </extensions>
      </idea-plugin>
    """.trimIndent()
    assertThat(collect(report, mapOf("intellij.sample" to descriptor)).single().error.message)
      .contains(" sample-rt.jar (declared in intellij.sample)")
      .doesNotContain("  sample-jps.jar (declared")
  }

  // The Draft layout of DevKit drops the extension from the packed descriptor, and the validation reads the module output.
  @Test
  fun `an ignored plugin with missing jars reports nothing`() {
    val report = PluginContentReport(mainModule = groovy, content = listOf(
      FileEntry(name = "lib/groovy.jar", modules = listOf(module(groovy), module("intellij.groovy.jps"))),
    ))
    assertThat(collect(report, mapOf(groovy to groovyPluginXml), ignoredPlugins = mapOf(groovy to "the layout drops the extension"))).isEmpty()
  }

  @Test
  fun `an ignored plugin without missing jars is reported as stale`() {
    val report = PluginContentReport(mainModule = groovy, content = listOf(
      FileEntry(name = "lib/Groovy.jar", modules = listOf(module(groovy))),
      FileEntry(name = "lib/groovy-jps.jar", modules = listOf(module("intellij.groovy.jps"))),
      FileEntry(name = "lib/groovy-constants-rt.jar", modules = listOf(module("intellij.groovy.constants.rt"))),
    ))
    (val name, val error) = collect(report, mapOf(groovy to groovyPluginXml), ignoredPlugins = mapOf(groovy to "the layout drops the extension")).single()
    assertThat(name).isEqualTo("compile-server-plugin-jars: stale ignored plugins")
    assertThat(error.message).contains("$groovy (the layout drops the extension)")
  }

  @Test
  fun `a plugin without the extension reports nothing`() {
    val report = PluginContentReport(mainModule = "intellij.sample", content = listOf(
      FileEntry(name = "lib/sample.jar", modules = listOf(module("intellij.sample"))),
    ))
    assertThat(collect(report, mapOf("intellij.sample" to "<idea-plugin><id>sample</id></idea-plugin>"))).isEmpty()
  }
}
