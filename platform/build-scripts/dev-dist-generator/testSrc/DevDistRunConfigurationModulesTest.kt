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
    runtimeModuleRepository: Boolean = false,
  ): DevRunConfigurationRow {
    return DevRunConfigurationRow(
      name = name,
      xmlFileName = "$name.xml",
      product = product,
      additionalModules = additionalModules,
      jvmFlags = jvmFlags,
      env = env,
      runtimeModuleRepository = runtimeModuleRepository,
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
      "\$\${BUILD_WORKSPACE_DIRECTORY}/../relative",
    )
    assertThat(rows.getValue("plain").programArgs).isEmpty()
  }

  @Test
  fun `a workspace-relative path value of a flag or a program argument becomes absolute`() {
    Files.createDirectories(dir.resolve("tools"))
    write("Paths.xml", devMain(
      "Paths",
      "-Didea.platform.prefix=idea -Da.entry=tools/x -Da.out=out/dev-data/x -Da.dot=./x -Da.up=../x -Da.home=~/x.log -Da.none=nope/x " +
      "-Da.suffix=conf/x.json -Da.url=https://h/x.json -Da.absolute=/opt/x.txt -Da.drive=C:/x/y.txt -Da.word=tools " +
      "-Da.class=com.a.B -Da.flag=true -Da.number=42 -Da.ids=a.b,c.d -Da.project=\$PROJECT_DIR\$/x",
      programParameters = "tools/x ./out/x ../x ~/x conf/x.xml https://h/x.json /opt/x.txt --config=conf/x.json tools nope/x",
    ))

    val row = readDevRunConfigurationRows(runConfigurations).single()

    assertThat(row.jvmFlags).containsExactly(
      "-Da.absolute=/opt/x.txt",
      "-Da.class=com.a.B",
      "-Da.dot=\$\${BUILD_WORKSPACE_DIRECTORY}/x",
      "-Da.drive=C:/x/y.txt",
      "-Da.entry=\$\${BUILD_WORKSPACE_DIRECTORY}/tools/x",
      "-Da.flag=true",
      "-Da.home=~/x.log",
      "-Da.ids=a.b,c.d",
      "-Da.none=nope/x",
      "-Da.number=42",
      "-Da.out=\$\${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/x",
      "-Da.project=\$\${BUILD_WORKSPACE_DIRECTORY}/x",
      "-Da.suffix=\$\${BUILD_WORKSPACE_DIRECTORY}/conf/x.json",
      "-Da.up=\$\${BUILD_WORKSPACE_DIRECTORY}/../x",
      "-Da.url=https://h/x.json",
      "-Da.word=tools",
    )
    assertThat(row.programArgs).containsExactly(
      "\$\${BUILD_WORKSPACE_DIRECTORY}/tools/x",
      "\$\${BUILD_WORKSPACE_DIRECTORY}/out/x",
      "\$\${BUILD_WORKSPACE_DIRECTORY}/../x",
      "~/x",
      "\$\${BUILD_WORKSPACE_DIRECTORY}/conf/x.xml",
      "https://h/x.json",
      "/opt/x.txt",
      "--config=conf/x.json",
      "tools",
      "nope/x",
    )
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

    assertThat(row.jvmFlags)
      .containsExactly("-Da=1", "-Didea.plugins.path=\$\${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/idea/config/edu-plugins")
    assertThat(row.env).isEmpty()
  }

  @Test
  fun `a runtime module repository path below out becomes the row attribute`() {
    write("Embedded.xml", devMain(
      "Embedded",
      "-Didea.platform.prefix=Gateway -Dintellij.platform.runtime.repository.path=\$PROJECT_DIR\$/out/classes/module-descriptors.jar -Da=1",
    ))
    write("Own.xml", devMain("Own", "-Didea.platform.prefix=Gateway -Dintellij.platform.runtime.repository.path=/opt/descriptors.dat"))
    write("Relative.xml", devMain(
      "Relative",
      "-Didea.platform.prefix=Gateway -Dintellij.platform.runtime.repository.path=out/descriptors.dat",
    ))

    val rows = readDevRunConfigurationRows(runConfigurations).associateBy { it.name }

    assertThat(rows.getValue("embedded").runtimeModuleRepository).isTrue()
    assertThat(rows.getValue("embedded").jvmFlags).containsExactly("-Da=1")
    assertThat(rows.getValue("own").runtimeModuleRepository).isFalse()
    assertThat(rows.getValue("own").jvmFlags).containsExactly("-Dintellij.platform.runtime.repository.path=/opt/descriptors.dat")
    assertThat(rows.getValue("relative").runtimeModuleRepository).isTrue()
    assertThat(rows.getValue("relative").jvmFlags).isEmpty()
  }

  @Test
  fun `a value that names a JPS output fails and names the file and the value`() {
    class Case(val fileName: String, val vmParameters: String, val env: Map<String, String>, val value: String)
    for (case in listOf(
      Case("Property.xml", "-Dx.dir=\$PROJECT_DIR\$/out/classes/production/x", emptyMap(), "\$PROJECT_DIR\$/out/classes/production/x"),
      Case("Agent.xml", "-javaagent:\$PROJECT_DIR\$/out/test/agent.jar", emptyMap(), "-javaagent:\$PROJECT_DIR\$/out/test/agent.jar"),
      Case("Variable.xml", "", mapOf("TOOL_DIR" to "\$PROJECT_DIR\$/out/production"), "\$PROJECT_DIR\$/out/production"),
      Case("Relative.xml", "-Dx.dir=out/classes/x", emptyMap(), "\$PROJECT_DIR\$/out/classes/x"),
    )) {
      write(case.fileName, devMain(case.fileName, "-Didea.platform.prefix=idea ${case.vmParameters}", env = case.env))

      assertThatThrownBy { readDevRunConfigurationRows(runConfigurations) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("${case.fileName}: '${case.value}' names a JPS output")
        .hasMessageContaining("-Dintellij.build.generate.runtime.module.repository=true")
      Files.delete(runConfigurations.resolve(case.fileName))
    }

    write("Kept.xml", devMain(
      "Kept",
      "-Didea.platform.prefix=idea -Dx.data=\$PROJECT_DIR\$/out/dev-data/x -Dx.frontend=\$PROJECT_DIR\$/out/local-rd-frontend/current " +
      "-Dx.other=\$PROJECT_DIR\$/out/classes-cache",
    ))
    assertThat(readDevRunConfigurationRows(runConfigurations).single().jvmFlags).containsExactly(
      "-Dx.data=\$\${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/x",
      "-Dx.frontend=\$\${BUILD_WORKSPACE_DIRECTORY}/out/local-rd-frontend/current",
      "-Dx.other=\$\${BUILD_WORKSPACE_DIRECTORY}/out/classes-cache",
    )
  }

  @Test
  fun `a variable that only the legacy engine reads is neither a flag nor env`() {
    write("Legacy.xml", devMain(
      "Legacy",
      "-Didea.platform.prefix=idea",
      env = mapOf("CLASSES_DIR" to "\$PROJECT_DIR\$/out/production", "K" to "v"),
    ))

    val row = readDevRunConfigurationRows(runConfigurations).single()

    assertThat(row.jvmFlags).isEmpty()
    assertThat(row.env).containsExactly(entry("K", "v"))
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
      |    "a": struct(product = "idea", distribution = "a", jvm_flags = _JVM_FLAGS_A),  # a.xml
      |    "b": struct(product = "idea", distribution = "a", jvm_flags = _JVM_FLAGS_A, env = {"K": "v"}),  # b.xml
      |    "c": struct(
      |        product = "other",  # c.xml
      |        distribution = "c",
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
      .contains("\"field\": struct(product = \"idea\", distribution = \"field\", jvm_flags = [\"-Da=1\"], x_field = True),  # Field.xml")
  }

  @Test
  fun `rows of one product, module set and repository share the distribution of the shortest name`() {
    val rows = listOf(
      row("idea_free_mode", jvmFlags = listOf("-Da=1")),
      row("idea_backend"),
      row("idea_air", additionalModules = listOf("intellij.air")),
      row("idea_mrs", additionalModules = listOf("intellij.air")),
      row("idea_rmr", runtimeModuleRepository = true),
    )

    val distributions = devRunDistributions(rows)

    assertThat(distributions.map { it.name to it.rows }).containsExactly(
      "idea_air" to listOf("idea_air", "idea_mrs"),
      "idea_backend" to listOf("idea_backend", "idea_free_mode"),
      "idea_rmr" to listOf("idea_rmr"),
    )
    // The group key keeps the list order, as the old Starlark tuple did. The reader sorts the modules, so two rows never differ only in order.
    assertThat(devRunDistributions(listOf(row("ab", additionalModules = listOf("a", "b")), row("ba", additionalModules = listOf("b", "a")))).map { it.name })
      .containsExactly("ab", "ba")
    assertThat(renderDevServerRunConfigurations(rows, splitProducts = setOf("idea"), macrosBzl = TEST_MACROS_BZL, header = TEST_HEADER))
      .contains("\"idea_free_mode\": struct(product = \"idea\", distribution = \"idea_backend\", jvm_flags = [\"-Da=1\"]),")
      .contains("\"idea_mrs\": struct(product = \"idea\", distribution = \"idea_air\", additional_modules = [\"intellij.air\"]),")
  }

  @Test
  fun `the distribution index states each named product once and lists each distribution`() {
    val rows = listOf(
      row("idea_backend"),
      row("idea"),
      row("idea_air", additionalModules = listOf("intellij.air", "intellij.ml")),
      row("client", product = "ideaJetBrainsClient", runtimeModuleRepository = true),
    )
    val products = mapOf(
      "idea" to DevDistIndexProduct(
        bundledModules = listOf("intellij.kotlin", "intellij.java"),
        platformBundledModules = mapOf("intellij.wsl" to listOf("windows_x64", "windows_aarch64"), "intellij.mac" to listOf("darwin_x64")),
        modularLoader = false,
      ),
      "ideaJetBrainsClient" to DevDistIndexProduct(bundledModules = emptyList(), platformBundledModules = emptyMap(), modularLoader = true),
      "unused" to DevDistIndexProduct(bundledModules = listOf("intellij.x"), platformBundledModules = emptyMap(), modularLoader = false),
    )

    assertThat(renderDevDistDistributionIndex(rows, products, generatorCommand = "bazel run //tool")).isEqualTo(
      """
      |{
      |    "generatedBy": "bazel run //tool",
      |    "products": {
      |        "idea": {
      |            "bundledModules": [
      |                "intellij.java",
      |                "intellij.kotlin"
      |            ],
      |            "platformBundledModules": {
      |                "intellij.mac": [
      |                    "darwin_x64"
      |                ],
      |                "intellij.wsl": [
      |                    "windows_aarch64",
      |                    "windows_x64"
      |                ]
      |            },
      |            "modularLoader": false
      |        },
      |        "ideaJetBrainsClient": {
      |            "bundledModules": [],
      |            "platformBundledModules": {},
      |            "modularLoader": true
      |        }
      |    },
      |    "distributions": [
      |        {
      |            "name": "client",
      |            "label": "//build:client_dist",
      |            "product": "ideaJetBrainsClient",
      |            "additionalModules": [],
      |            "runtimeModuleRepository": true
      |        },
      |        {
      |            "name": "idea",
      |            "label": "//build:idea_dist",
      |            "product": "idea",
      |            "additionalModules": [],
      |            "runtimeModuleRepository": false
      |        },
      |        {
      |            "name": "idea_air",
      |            "label": "//build:idea_air_dist",
      |            "product": "idea",
      |            "additionalModules": [
      |                "intellij.air",
      |                "intellij.ml"
      |            ],
      |            "runtimeModuleRepository": false
      |        }
      |    ]
      |}
      |""".trimMargin()
    )
  }

  @Test
  fun `a bundled component of some host platforms leaves the modules of every platform`() {
    val bundled = devDistBundledModules(listOf(
      GeneratedPluginComponent("intellij.b", label = "//b"),
      GeneratedPluginComponent("intellij.all", labels = HOST_PLATFORMS.associateWith { "//all_$it" }),
      GeneratedPluginComponent("intellij.wsl", labels = mapOf("windows_x64" to "//wsl_x64", "windows_aarch64" to "//wsl_aarch64")),
      GeneratedPluginComponent("intellij.a", label = "//a"),
    ))

    assertThat(bundled.modules).containsExactly("intellij.a", "intellij.all", "intellij.b")
    assertThat(bundled.platformModules).containsExactly(entry("intellij.wsl", listOf("windows_aarch64", "windows_x64")))
  }

  @Test
  fun `a community product takes the index facts of the community half`() {
    val planned = DevDistIndexProduct(bundledModules = listOf("intellij.u"), platformBundledModules = emptyMap(), modularLoader = false)
    val community = DevDistIndexProduct(bundledModules = listOf("intellij.c"), platformBundledModules = emptyMap(), modularLoader = true)

    val products = devDistIndexProducts(mapOf("idea" to planned), communityProducts = setOf("Idea"), upstream = mapOf("Idea" to community, "Other" to planned))

    assertThat(products).containsExactly(entry("Idea", community), entry("idea", planned))
    assertThatThrownBy { devDistIndexProducts(emptyMap(), communityProducts = setOf("Idea"), upstream = emptyMap()) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'Idea'")
  }

  @Test
  fun `a distribution of a product without index facts fails the index`() {
    assertThatThrownBy { renderDevDistDistributionIndex(listOf(row("g", product = "Other")), emptyMap(), generatorCommand = "test") }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'g'")
      .hasMessageContaining("'Other'")
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
