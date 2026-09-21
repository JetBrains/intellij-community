// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** The Bazel test environment names `testData/run-configurations/expected.txt` here; an IDE run reads the checkout. */
private const val FIXTURES_MARKER_ENV = "BAZEL_GENERATOR_RUN_CONFIGURATIONS_TEST_DATA_MARKER"

/**
 * The converter derives one [DevServerRunConfiguration] per `DevMainKt` run configuration: the product, the additional
 * modules, the launcher flags and the three feature attributes. [RunConfigurationsFile] renders each row as one
 * `intellij_dev_run_configuration` call.
 *
 * The plan generator reads the same files with a reader of its own, `readDevDistRunConfigurationModules`. The fixture
 * set under `testData/run-configurations` and its `expected.txt` are shared with that reader's test, so both give one
 * answer.
 */
internal class RunConfigurationsFileTest {

  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `the product is the platform prefix`() {
    val row = row("""-Didea.platform.prefix=PyCharm -Xmx2g""")
    assertEquals("PyCharm", row.product)
    assertEquals("PyCharm", row.platformPrefix)
    assertEquals(listOf("-Xmx2g"), row.jvmFlags)
  }

  @Test
  fun `the product of a frontend is the base prefix plus the platform prefix`() {
    val row = row("""-Didea.platform.prefix=JetBrainsClient -Ddev.build.base.ide.platform.prefix.for.frontend=GoLand""")
    assertEquals("GoLandJetBrainsClient", row.product)
    assertEquals("JetBrainsClient", row.platformPrefix)
    assertEquals(listOf("-Ddev.build.base.ide.platform.prefix.for.frontend=GoLand"), row.jvmFlags)
  }

  @Test
  fun `the frontend rule applies to any prefix`() {
    val row = row("""-Didea.platform.prefix=Other -Ddev.build.base.ide.platform.prefix.for.frontend=idea""")
    assertEquals("ideaOther", row.product)
  }

  @Test
  fun `the additional modules are split and trimmed in xml order, and leave the jvm flags`() {
    val row = row(
      """-Didea.platform.prefix=idea -Dadditional.modules=&quot;intellij.devkit, intellij.air.plugin&quot; -Didea.is.internal=true"""
    )
    assertEquals(listOf("intellij.devkit", "intellij.air.plugin"), row.additionalModules)
    assertEquals(listOf("-Didea.is.internal=true"), row.jvmFlags)
    assertFalse(row.jvmFlags.any { it.startsWith("-Dadditional.modules=") })
    assertFalse(row.customCommand)
    assertFalse(row.generateRuntimeModuleRepository)
    assertFalse(row.compileClionBackendBeforeRun)
  }

  @Test
  fun `a custom command is an attribute and leaves the jvm flags`() {
    val row = row(
      "-Didea.platform.prefix=JetBrainsClient -Ddev.build.base.ide.platform.prefix.for.frontend=idea " +
      "-Didea.dev.mode.custom.command=true"
    )
    assertTrue(row.customCommand)
    assertEquals(listOf("-Ddev.build.base.ide.platform.prefix.for.frontend=idea"), row.jvmFlags)
  }

  @Test
  fun `a runtime module repository is an attribute and leaves the jvm flags`() {
    val row = row("""-Didea.platform.prefix=idea -Dintellij.build.generate.runtime.module.repository=true""")
    assertTrue(row.generateRuntimeModuleRepository)
    assertEquals(emptyList<String>(), row.jvmFlags)
  }

  @Test
  fun `the CLion before-run step is an attribute and leaves the jvm flags`() {
    val row = row("""-Didea.platform.prefix=idea -Dintellij.build.dev.server.compile.clion.backend.before.run=true""")
    assertTrue(row.compileClionBackendBeforeRun)
    assertEquals(emptyList<String>(), row.jvmFlags)
  }

  @Test
  fun `a feature property that is not true is off`() {
    val row = row("""-Didea.platform.prefix=idea -Dintellij.build.dev.server.compile.clion.backend.before.run=false""")
    assertFalse(row.compileClionBackendBeforeRun)
    assertEquals(emptyList<String>(), row.jvmFlags)
  }

  @Test
  fun `a module the project does not have keeps the row`() {
    val row = row("""-Didea.platform.prefix=idea -Dadditional.modules=intellij.devkit,intellij.missing""")
    assertEquals(listOf("intellij.devkit", "intellij.missing"), row.additionalModules)
  }

  @Test
  fun `the rows are the DevMainKt configurations in xml file order`() {
    val root = tempFolder.newFolder("project").toPath()
    val runConfigurations = root.resolve(".idea/runConfigurations").createDirectories()
    runConfigurations.resolve("Zed.xml").writeText(
      devMainConfiguration("Zed", """-Didea.platform.prefix=idea -Dadditional.modules=intellij.devkit""")
    )
    runConfigurations.resolve("Alpha_Air.xml").writeText(
      devMainConfiguration("Alpha Air", """-Didea.platform.prefix=idea -Dadditional.modules=&quot;intellij.devkit,intellij.air.plugin&quot;""")
    )
    runConfigurations.resolve("Kept.xml").writeText(
      devMainConfiguration("Kept", """-Didea.platform.prefix=idea -Didea.dev.mode.custom.command=true""")
    )
    runConfigurations.resolve("Other_Main.xml").writeText(
      devMainConfiguration("Other Main", """-Didea.platform.prefix=idea""", mainClass = "com.example.OtherMainKt")
    )
    runConfigurations.resolve("Rider.Generated.xml").writeText(
      devMainConfiguration("Generated", """-Didea.platform.prefix=Rider""")
    )

    val rows = devServerRunConfigurations(root)

    assertEquals(listOf("alpha_air", "kept", "zed"), rows.map { it.name })
    assertEquals(listOf(false, true, false), rows.map { it.customCommand })
    assertEquals(listOf("intellij.devkit", "intellij.air.plugin"), rows.first().additionalModules)
  }

  @Test
  fun `the shared fixture set gives the additional modules by product`() {
    val fixtures = sharedFixtures()
    val root = tempFolder.newFolder("project").toPath()
    val runConfigurations = root.resolve(".idea/runConfigurations").createDirectories()
    Files.list(fixtures).use { files ->
      files.filter { it.name.endsWith(".xml") }.forEach { Files.copy(it, runConfigurations.resolve(it.name)) }
    }
    val expected = TreeMap<String, List<String>>()
    for (line in Files.readAllLines(fixtures.resolve("expected.txt"))) {
      if (line.isBlank()) continue
      expected.put(line.substringBefore(':').trim(), line.substringAfter(':').split(',').map { it.trim() })
    }

    val actual = TreeMap<String, TreeSet<String>>()
    for (row in devServerRunConfigurations(root)) {
      actual.computeIfAbsent(row.product) { TreeSet() }.addAll(row.additionalModules)
    }

    assertEquals(expected, actual.mapValues { it.value.toList() })
  }

  @Test
  fun `a plain configuration renders the run configuration macro`() {
    val rendered = rendered(
      "Idea Air" to """-Didea.platform.prefix=idea -Dadditional.modules=&quot;intellij.devkit, intellij.air.plugin&quot; -Didea.is.internal=true""",
    )
    assertEquals(
      """
      |load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configuration(
      |        #xmlFile = "Idea_Air.xml",
      |        name = "idea_air",
      |        product = "idea",
      |        platform_prefix = "idea",
      |        additional_modules = [
      |            "intellij.devkit",
      |            "intellij.air.plugin",
      |        ],
      |        jvm_flags = ["-Didea.is.internal=true"],
      |    )
      |""".trimMargin(),
      rendered,
    )
  }

  @Test
  fun `a frontend renders the base product and keeps the base flag`() {
    val rendered = rendered(
      "IDEA Frontend" to """-Didea.platform.prefix=JetBrainsClient -Ddev.build.base.ide.platform.prefix.for.frontend=idea -Xmx2g""",
    )
    assertEquals(
      """
      |load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configuration(
      |        #xmlFile = "IDEA_Frontend.xml",
      |        name = "idea_frontend",
      |        product = "ideaJetBrainsClient",
      |        platform_prefix = "JetBrainsClient",
      |        jvm_flags = [
      |            "-Ddev.build.base.ide.platform.prefix.for.frontend=idea",
      |            "-Xmx2g",
      |        ],
      |    )
      |""".trimMargin(),
      rendered,
    )
  }

  @Test
  fun `a custom command renders the attribute`() {
    val rendered = rendered(
      "Light Mode" to """-Didea.platform.prefix=JetBrainsClient -Ddev.build.base.ide.platform.prefix.for.frontend=idea -Didea.dev.mode.custom.command=true""",
    )
    assertEquals(
      """
      |load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configuration(
      |        #xmlFile = "Light_Mode.xml",
      |        name = "light_mode",
      |        product = "ideaJetBrainsClient",
      |        platform_prefix = "JetBrainsClient",
      |        jvm_flags = ["-Ddev.build.base.ide.platform.prefix.for.frontend=idea"],
      |        custom_command = True,
      |    )
      |""".trimMargin(),
      rendered,
    )
  }

  @Test
  fun `a runtime module repository renders the attribute and keeps the modules as modules`() {
    val rendered = rendered(
      "Repo" to """-Didea.platform.prefix=idea -Dadditional.modules=intellij.devkit,intellij.air.plugin -Dintellij.build.generate.runtime.module.repository=true""",
    )
    assertEquals(
      """
      |load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configuration(
      |        #xmlFile = "Repo.xml",
      |        name = "repo",
      |        product = "idea",
      |        platform_prefix = "idea",
      |        additional_modules = [
      |            "intellij.devkit",
      |            "intellij.air.plugin",
      |        ],
      |        jvm_flags = [],
      |        generate_runtime_module_repository = True,
      |    )
      |""".trimMargin(),
      rendered,
    )
  }

  @Test
  fun `the CLion before-run step renders the attribute after the env`() {
    val rendered = rendered(
      "CLion Step" to """-Didea.platform.prefix=idea -Dintellij.build.dev.server.compile.clion.backend.before.run=true""",
      env = mapOf("CWM_NO_TIMEOUTS" to "1"),
    )
    assertEquals(
      """
      |load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configuration(
      |        #xmlFile = "CLion_Step.xml",
      |        name = "clion_step",
      |        product = "idea",
      |        platform_prefix = "idea",
      |        jvm_flags = [],
      |        env = {
      |            "CWM_NO_TIMEOUTS": "1",
      |        },
      |        compile_clion_backend_before_run = True,
      |    )
      |""".trimMargin(),
      rendered,
    )
  }

  @Test
  fun `the load line names the one macro for every row`() {
    val rendered = rendered(
      "Kept" to """-Didea.platform.prefix=idea -Didea.dev.mode.custom.command=true""",
      "Split" to """-Didea.platform.prefix=idea""",
    )
    assertEquals(
      """load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")""",
      rendered.lines().first(),
    )
    assertEquals(2, rendered.lines().count { it.endsWith("(") })
  }

  @Test
  fun `the written file is one generated section`() {
    val root = tempFolder.newFolder("project").toPath()
    val runConfigurations = root.resolve(".idea/runConfigurations").createDirectories()
    runConfigurations.resolve("Split.xml").writeText(devMainConfiguration("Split", """-Didea.platform.prefix=idea"""))
    val target = root.resolve("build/dev_server_run_configurations.bzl")

    saveDevServerRunConfigurations(ultimateRoot = root, targetFilePath = target)
    val written = target.readText()

    assertEquals(
      """
      |### auto-generated section `devServer-runs` start
      |load("//build:intellij_dev_ultimate.bzl", "intellij_dev_run_configuration")
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configuration(
      |        #xmlFile = "Split.xml",
      |        name = "split",
      |        product = "idea",
      |        platform_prefix = "idea",
      |        jvm_flags = [],
      |    )
      |
      |### auto-generated section `devServer-runs` end
      |""".trimMargin(),
      written,
    )

    saveDevServerRunConfigurations(ultimateRoot = root, targetFilePath = target)
    assertEquals(written, target.readText())
  }

  private fun rendered(vararg configurations: Pair<String, String>, env: Map<String, String> = emptyMap()): String {
    val root = tempFolder.newFolder().toPath()
    val runConfigurations = root.resolve(".idea/runConfigurations").createDirectories()
    for ((name, vmParameters) in configurations) {
      runConfigurations.resolve(name.replace(' ', '_') + ".xml").writeText(devMainConfiguration(name, vmParameters, env = env))
    }
    val file = RunConfigurationsFile()
    for (row in devServerRunConfigurations(root)) {
      file.generateDevServerRunConfiguration(row)
    }
    return file.render()
  }

  private fun sharedFixtures(): Path {
    val marker = System.getenv(FIXTURES_MARKER_ENV)
    if (marker.isNullOrBlank()) {
      return Path.of("testData/run-configurations")
    }
    return Path.of(System.getenv("TEST_SRCDIR"), marker).parent
  }

  private fun row(vmParameters: String): DevServerRunConfiguration {
    val root = tempFolder.newFolder().toPath()
    val xmlFile = root.resolve(".idea/runConfigurations/Sample.xml").createParentDirectories()
    xmlFile.writeText(devMainConfiguration("Sample", vmParameters))
    val (file, spec) = loadRunConfigurations(root).entries.single()
    return devServerRunConfiguration(xmlFile = file, spec = spec)
  }

  private fun devMainConfiguration(
    name: String,
    vmParameters: String,
    mainClass: String = "org.jetbrains.intellij.build.devServer.DevMainKt",
    env: Map<String, String> = emptyMap(),
  ): String {
    val envs = if (env.isEmpty()) "" else env.entries.joinToString(
      separator = "\n",
      prefix = "<envs>\n",
      postfix = "\n</envs>",
    ) { (key, value) -> """<env name="$key" value="$value" />""" }
    return """
      <component name="ProjectRunConfigurationManager">
        <configuration default="false" name="$name" type="Application" factoryName="Application">
          <option name="MAIN_CLASS_NAME" value="$mainClass" />
          <module name="intellij.platform.bootstrap.dev" />
          <option name="VM_PARAMETERS" value="$vmParameters" />
          $envs
        </configuration>
      </component>
    """.trimIndent()
  }
}
