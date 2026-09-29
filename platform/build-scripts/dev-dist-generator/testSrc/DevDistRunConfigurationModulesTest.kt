// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The generator reads the dev-server run configurations of `.idea/runConfigurations` itself: the rows it writes into
 * `dev_server_run_configurations.bzl`, and the additional plugin modules of each split product.
 */
class DevDistRunConfigurationModulesTest {
  @TempDir
  lateinit var dir: Path

  private val runConfigurations: Path
    get() = dir.resolve(".idea/runConfigurations")

  private fun write(fileName: String, content: String) {
    Files.createDirectories(runConfigurations)
    Files.writeString(runConfigurations.resolve(fileName), content)
  }

  private fun devMain(
    name: String,
    vmParameters: String,
    mainClass: String = "org.jetbrains.intellij.build.devServer.DevMainKt",
    env: Map<String, String> = emptyMap(),
    programParameters: String? = null,
  ): String {
    val envs = if (env.isEmpty()) "" else env.entries.joinToString(prefix = "<envs>", postfix = "</envs>", separator = "") { (key, value) ->
      "<env name=\"$key\" value=\"$value\" />"
    }
    val program = if (programParameters == null) "" else "<option name=\"PROGRAM_PARAMETERS\" value=\"$programParameters\" />"
    return """
      <component name="ProjectRunConfigurationManager">
        <configuration default="false" name="$name" type="Application" factoryName="Application">
          $envs
          <option name="MAIN_CLASS_NAME" value="$mainClass" />
          <module name="intellij.platform.bootstrap.dev.legacy" />
          $program
          <option name="VM_PARAMETERS" value="$vmParameters" />
        </configuration>
      </component>
    """.trimIndent()
  }

  private fun row(
    name: String,
    product: String = "idea",
    additionalModules: List<String> = emptyList(),
    jvmFlags: List<String> = emptyList(),
    env: Map<String, String> = emptyMap(),
  ): DevRunConfigurationRow {
    return DevRunConfigurationRow(
      name = name,
      xmlFileName = "$name.xml",
      product = product,
      additionalModules = additionalModules,
      jvmFlags = jvmFlags,
      env = env,
      runtimeModuleRepository = false,
    )
  }

  private fun read(vararg splitProducts: String): Map<String, List<String>> {
    return devDistRunConfigurationModules(readDevRunConfigurationRows(runConfigurations), splitProducts.toSet())
  }

  @Test
  fun `the union of a product is sorted and distinct`() {
    write("Idea_C.xml", devMain("Idea C", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.c,intellij.a"))
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Xmx2g"))
    write("Idea_B.xml", devMain("Idea B", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.b, intellij.a,"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a", "intellij.b", "intellij.c")))
  }

  @Test
  fun `a quoted module list keeps its spaces and loses its quotes`() {
    write("Mcp.xml", devMain("MCP", "-Didea.platform.prefix=idea -Dadditional.modules=&quot;intellij.b, intellij.a&quot; -Didea.is.internal=false"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a", "intellij.b")))
  }

  @Test
  fun `only a DevMainKt configuration counts`() {
    write("Other.xml", devMain("Other", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.other", mainClass = "com.example.MainKt"))
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a")))
  }

  @Test
  fun `a generated file is skipped`() {
    write("Other.Generated.xml", devMain("Generated", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.generated"))
    write("Kotlin_Generated_Tests.xml", devMain("Kotlin Generated Tests", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a")))
  }

  @Test
  fun `the product of a frontend is the base prefix plus the platform prefix`() {
    write("Frontend.xml", devMain(
      "Frontend",
      "-Didea.platform.prefix=Client -Ddev.build.base.ide.platform.prefix.for.frontend=idea -Dadditional.modules=intellij.frontend",
    ))
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))

    assertThat(read("idea", "ideaClient"))
      .containsExactly(entry("idea", listOf("intellij.a")), entry("ideaClient", listOf("intellij.frontend")))
  }

  @Test
  fun `a product the split map does not name is skipped`() {
    write("Other.xml", devMain("Other", "-Didea.platform.prefix=Other -Dadditional.modules=intellij.other.extra"))
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a")))
  }

  @Test
  fun `a configuration with a feature attribute contributes its modules`() {
    write("Command.xml", devMain("Command", "-Didea.platform.prefix=idea -Didea.dev.mode.custom.command=true -Dadditional.modules=intellij.command"))
    write("Repository.xml", devMain(
      "Repository",
      "-Didea.platform.prefix=idea -Dintellij.build.generate.runtime.module.repository=true -Dadditional.modules=intellij.repository",
    ))
    write("Feature.xml", devMain("Feature", "-Didea.platform.prefix=idea -Dx.feature=true -Dadditional.modules=intellij.feature"))
    write("Feature_Off.xml", devMain("Feature off", "-Didea.platform.prefix=idea -Dx.feature=false -Dadditional.modules=intellij.a"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a", "intellij.command", "intellij.feature", "intellij.repository")))
  }

  @Test
  fun `a product with a runtime module repository row is reported once`() {
    write("Repository.xml", devMain("Repository", "-Didea.platform.prefix=idea -Dintellij.build.generate.runtime.module.repository=true"))
    write("Repository_2.xml", devMain("Repository 2", "-Didea.platform.prefix=idea -Dintellij.build.generate.runtime.module.repository=true -Xmx2g"))
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))
    write("Off.xml", devMain("Off", "-Didea.platform.prefix=Other -Dintellij.build.generate.runtime.module.repository=false"))
    write("Unsplit.xml", devMain("Unsplit", "-Didea.platform.prefix=Unsplit -Dintellij.build.generate.runtime.module.repository=true"))

    val rows = readDevRunConfigurationRows(runConfigurations)
    assertThat(devDistRuntimeModuleRepositoryProducts(rows, setOf("idea", "Other"))).containsExactly("idea")
  }

  @Test
  fun `a missing directory reads as no runtime module repository product`() {
    assertThat(devDistRuntimeModuleRepositoryProducts(readDevRunConfigurationRows(runConfigurations), setOf("idea"))).isEmpty()
  }

  @Test
  fun `a missing platform prefix fails and names the file`() {
    write("Broken.xml", devMain("Broken", "-Dadditional.modules=intellij.a"))

    assertThatThrownBy { read("idea") }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("idea.platform.prefix")
      .hasMessageContaining("Broken.xml")
  }

  @Test
  fun `a missing directory reads as no module`() {
    assertThat(read("idea")).isEmpty()
  }

  @Test
  fun `a file the parser rejects is skipped and the other files count`() {
    write("Broken.xml", devMain("Broken", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.broken").substringBefore("</component>"))
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.a")))
  }

  @Test
  fun `a file with several configurations counts the last one`() {
    val first = devMain("First", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.first").substringAfter("<component name=\"ProjectRunConfigurationManager\">").substringBefore("</component>")
    val second = devMain("Second", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.second").substringAfter("<component name=\"ProjectRunConfigurationManager\">").substringBefore("</component>")
    write("Two.xml", "<component name=\"ProjectRunConfigurationManager\">$first$second</component>")

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.second")))
  }

  @Test
  fun `the quotes around the whole parameter string are dropped and a quote inside a value stays`() {
    write("Surrounded.xml", devMain("Surrounded", "&quot;-Didea.platform.prefix=idea -Dadditional.modules=intellij.s&quot;"))
    write("Value.xml", devMain("Value", "-Didea.platform.prefix=idea -Dx=a&quot;b -Dadditional.modules=intellij.q"))

    assertThat(read("idea")).containsExactly(entry("idea", listOf("intellij.q", "intellij.s")))
  }

  @Test
  fun `a split product with no run configuration has no row`() {
    write("Idea.xml", devMain("Idea", "-Didea.platform.prefix=idea -Dadditional.modules=intellij.a"))

    val result = read("Other", "idea")

    assertThat(result).doesNotContainKey("Other")
    assertThat(result).containsKey("idea")
  }

  @Test
  fun `a row states its launch flags sorted and its fields apart`() {
    write("IDEA__air_.xml", devMain(
      "IDEA (air)",
      "-Didea.platform.prefix=idea -Xmx8000m -Dadditional.modules=&quot;intellij.devkit,intellij.air.plugin,intellij.devkit&quot; " +
      "-Dintellij.build.generate.runtime.module.repository=true -Didea.dev.mode.custom.command=true -Da=1",
      env = mapOf("K" to "C:\\v", "TOOL_HOME" to "\$PROJECT_DIR\$/tools/bin"),
    ))

    val row = readDevRunConfigurationRows(runConfigurations).single()

    assertThat(row.name).isEqualTo("idea_air")
    assertThat(row.xmlFileName).isEqualTo("IDEA__air_.xml")
    assertThat(row.product).isEqualTo("idea")
    assertThat(row.additionalModules).containsExactly("intellij.air.plugin", "intellij.devkit")
    assertThat(row.jvmFlags).containsExactly(
      "-Da=1",
      "-Didea.dev.mode.custom.command=true",
      "-Dtool.home=\$\${BUILD_WORKSPACE_DIRECTORY}/tools/bin",
      "-Xmx8000m",
    )
    assertThat(row.env).containsExactly(entry("K", "C:/v"))
    assertThat(row.runtimeModuleRepository).isTrue()
    assertThat(row.booleanFields).isEmpty()
  }

  @Test
  fun `program parameters become Bazel args that the launcher expands`() {
    write("Light.xml", devMain(
      "Light",
      "-Didea.platform.prefix=idea",
      programParameters = "ijLight /\$tcp.ij/\$PROJECT_DIR\$/data &quot;a b&quot; \$USER_HOME\$/x ../relative",
    ))
    write("Plain.xml", devMain("Plain", "-Didea.platform.prefix=idea"))

    val rows = readDevRunConfigurationRows(runConfigurations).associateBy { it.name }

    assertThat(rows.getValue("light").programArgs).containsExactly(
      "ijLight",
      "/\$\$tcp.ij/\$\${BUILD_WORKSPACE_DIRECTORY}/data",
      "a b",
      "\$\${HOME}/x",
      "../relative",
    )
    assertThat(rows.getValue("plain").programArgs).isEmpty()
  }

  @Test
  fun `a row states its product and leaves both product selectors out of its flags`() {
    write("Frontend.xml", devMain("Frontend", "-Didea.platform.prefix=Client -Ddev.build.base.ide.platform.prefix.for.frontend=idea -Dawt.toolkit.name=auto"))

    val row = readDevRunConfigurationRows(runConfigurations).single()

    assertThat(row.product).isEqualTo("ideaClient")
    assertThat(row.jvmFlags).containsExactly("-Dawt.toolkit.name=auto")
  }

  @Test
  fun `a row leaves the data directories to the launcher and keeps its plugins directory`() {
    write("Classic.xml", devMain(
      "Classic",
      "-Didea.platform.prefix=idea -Didea.config.path=out/dev-data/classic/config -Didea.system.path=../system/classic " +
      "-Didea.log.path=../system/classic/log -Didea.plugins.path=out/dev-data/idea/config/edu-plugins -Da=1",
      env = mapOf("IDEA_SYSTEM_PATH" to "\$PROJECT_DIR\$/system"),
    ))

    val row = readDevRunConfigurationRows(runConfigurations).single()

    assertThat(row.jvmFlags).containsExactly("-Da=1", "-Didea.plugins.path=out/dev-data/idea/config/edu-plugins")
    assertThat(row.env).isEmpty()
  }

  @Test
  fun `a property the VM options and a project-directory variable both set fails`() {
    write("Conflict.xml", devMain(
      "Conflict",
      "-Didea.platform.prefix=idea -Dtool.home=/opt",
      env = mapOf("TOOL_HOME" to "\$PROJECT_DIR\$/tools"),
    ))

    assertThatThrownBy { readDevRunConfigurationRows(runConfigurations) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Conflict.xml")
  }

  @Test
  fun `two configurations with one launcher name fail and name both`() {
    write("A.xml", devMain("IDEA (dev)", "-Didea.platform.prefix=idea"))
    write("B.xml", devMain("IDEA dev", "-Didea.platform.prefix=idea"))

    assertThatThrownBy { readDevRunConfigurationRows(runConfigurations) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("idea_dev")
      .hasMessageContaining("A.xml")
      .hasMessageContaining("B.xml")
  }

  @Test
  fun `a launcher name loses diacritics, spells out languages and joins the rest with underscores`() {
    assertThat(sanitizeRunConfigurationName(" Tool (C# / F#) Café ")).isEqualTo("tool_csharp_fsharp_cafe")
    assertThat(sanitizeRunConfigurationName("Editor C++")).isEqualTo("editor_cpp")
  }

  @Test
  fun `the rendered rows share a flag list and keep a simple row on one line`() {
    val shared = listOf("-Dawt.toolkit.name=auto", "-Dx=\"q\"")
    val text = renderDevServerRunConfigurations(
      rows = listOf(
        row("b", jvmFlags = shared, env = mapOf("K" to "v")),
        row("a", jvmFlags = shared),
        row("c", product = "other", additionalModules = listOf("intellij.m", "intellij.n"), jvmFlags = listOf("-Xmx2g")),
      ),
      splitProducts = setOf("idea", "other"),
      macrosBzl = TEST_MACROS_BZL,
      header = TEST_HEADER,
    )

    assertThat(text).contains(
      """
      |_JVM_FLAGS_A = [
      |    "-Dawt.toolkit.name=auto",
      |    "-Dx=\"q\"",
      |]
      |
      |DEV_RUN_CONFIGURATIONS = {
      |    "a": struct(product = "idea", jvm_flags = _JVM_FLAGS_A),  # a.xml
      |    "b": struct(product = "idea", jvm_flags = _JVM_FLAGS_A, env = {"K": "v"}),  # b.xml
      |    "c": struct(
      |        product = "other",  # c.xml
      |        additional_modules = [
      |            "intellij.m",
      |            "intellij.n",
      |        ],
      |        jvm_flags = ["-Xmx2g"],
      |    ),
      |}
      |
      |def dev_server_run_configurations():
      |    intellij_dev_run_configurations(DEV_RUN_CONFIGURATIONS)
      |""".trimMargin()
    )
  }

  @Test
  fun `the community half reads the rows of its root and loads the community macros`() {
    write("Root.xml", devMain("Root", "-Didea.platform.prefix=idea"))
    val half = CommunityDevDistHalf
    val runConfigurations = half.root(dir).resolve(RUN_CONFIGURATIONS_DIRECTORY)
    Files.createDirectories(runConfigurations)
    Files.writeString(runConfigurations.resolve("IDEA_Community.xml"), devMain("IDEA Community", "-Didea.platform.prefix=Idea"))

    val rows = readDevRunConfigurationRows(runConfigurations)
    assertThat(rows.map { it.name }).containsExactly("idea_community")

    val splitProducts = half.registrySplitProducts(listOf("community", "Idea"))
    val text = renderDevServerRunConfigurations(rows, splitProducts = splitProducts, macrosBzl = half.macrosBzl, header = half.generatedByHeader)
    assertThat(text).contains("load(\"//build:intellij_dev_community.bzl\", \"intellij_dev_run_configurations\")")
    assertThat(text).contains("see `intellij_dev_run_configurations` in `intellij_dev_community.bzl`")
    assertThat(text).containsOnlyOnce("load(")
  }

  @Test
  fun `a community row of a product outside the community registry fails the rendering`() {
    val half = CommunityDevDistHalf

    assertThatThrownBy {
      renderDevServerRunConfigurations(
        rows = listOf(row("c", product = "community")),
        splitProducts = half.registrySplitProducts(listOf("community", "Idea")),
        macrosBzl = half.macrosBzl,
        header = half.generatedByHeader,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("product 'community' has no dev-distribution plan")
  }

  @Test
  fun `a row of a product without a plan fails the rendering`() {
    assertThatThrownBy { renderDevServerRunConfigurations(listOf(row("g", product = "Other")), splitProducts = setOf("idea"), macrosBzl = TEST_MACROS_BZL, header = TEST_HEADER) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Other")
      .hasMessageContaining("split distributions")
  }

  @Test
  fun `a row that sets a property the half refuses fails the rendering`() {
    val override = row("o", jvmFlags = listOf("-Dx.root=intellij.x"))

    assertThatThrownBy {
      renderDevServerRunConfigurations(
        rows = listOf(override),
        splitProducts = setOf("idea"),
        macrosBzl = TEST_MACROS_BZL,
        refusedProperties = mapOf("x.root" to "state the root module in the registry"),
        header = TEST_HEADER,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("o.xml: state the root module in the registry, not as -Dx.root")
  }

  @Test
  fun `a field property of the half becomes a row field and leaves the flags`() {
    write("Field.xml", devMain("Field", "-Didea.platform.prefix=idea -Dx.field=true -Dx.off=false -Da=1"))

    val row = readDevRunConfigurationRows(runConfigurations, fieldProperties = linkedMapOf("x.field" to "x_field", "x.off" to "x_off")).single()

    assertThat(row.booleanFields).containsExactly("x_field")
    assertThat(row.jvmFlags).containsExactly("-Da=1")
    assertThat(renderDevServerRunConfigurations(listOf(row), splitProducts = setOf("idea"), macrosBzl = TEST_MACROS_BZL, header = TEST_HEADER))
      .contains("\"field\": struct(product = \"idea\", jvm_flags = [\"-Da=1\"], x_field = True),  # Field.xml")
  }

  @Test
  fun `a module the plan cannot state fails and the message names every such module`() {
    val reasons = mapOf("intellij.missing" to "module does not exist", "intellij.util" to "no plugin descriptor")

    assertThatThrownBy {
      devDistRunConfigurationModules(
        named = mapOf("other" to listOf("intellij.util"), "idea" to listOf("intellij.a", "intellij.missing")),
        moduleReason = reasons::get,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("[other/intellij.util (no plugin descriptor), idea/intellij.missing (module does not exist)]")
      .hasMessageContaining("remove it from -Dadditional.modules")

    val result = devDistRunConfigurationModules(named = mapOf("idea" to listOf("intellij.a")), moduleReason = reasons::get)
    assertThat(result).containsExactly(entry("idea", listOf("intellij.a")))
  }
}

/** The macro file of a synthetic half. */
private const val TEST_MACROS_BZL: String = "//build:intellij_dev_test.bzl"

/** The header of a synthetic half. */
private const val TEST_HEADER: String = "# Generated by `test` - do not edit.\n"
