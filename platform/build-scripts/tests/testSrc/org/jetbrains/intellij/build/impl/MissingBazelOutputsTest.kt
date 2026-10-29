// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class MissingBazelOutputsTest {
  @Test
  fun `outside Bazel the error names every absent jar of a module and the command`(@TempDir tempDir: Path) {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.demo", JpsJavaModuleType.INSTANCE)
    val provider = provider(
      tempDir,
      modules = mapOf(
        module.name to moduleDescription(
          targets = listOf("@community//demo:demo.jar", "@community//demo:demo_resource_jar.jar"),
          jars = listOf("out/bazel-out/demo/demo.jar", "out/bazel-out/demo/demo_resource_jar.jar"),
        ),
      ),
    )

    assertThatThrownBy { provider.getModuleOutputRoots(module, forTests = false) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageStartingWith("2 jars of module 'intellij.demo' do not exist:\n")
      .hasMessageContaining("  @community//demo:demo.jar (module 'intellij.demo'): ${tempDir.resolve("out/bazel-out/demo/demo.jar")}\n")
      .hasMessageEndingWith("Build them: ./bazel.cmd build @community//demo:demo.jar @community//demo:demo_resource_jar.jar")
      .hasMessageNotContaining("bazel-build-all")
  }

  @Test
  fun `outside Bazel a present jar is a root`(@TempDir tempDir: Path) {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.demo", JpsJavaModuleType.INSTANCE)
    val jar = tempDir.resolve("out/bazel-out/demo/demo.jar")
    Files.createDirectories(jar.parent)
    Files.writeString(jar, "")
    val provider = provider(
      tempDir,
      modules = mapOf(
        module.name to moduleDescription(targets = listOf("@community//demo:demo.jar"), jars = listOf("out/bazel-out/demo/demo.jar")),
      ),
    )

    assertThat(provider.getModuleOutputRoots(module, forTests = false)).containsExactly(jar)
  }

  @Test
  fun `outside Bazel the error names the label of an absent library jar`(@TempDir tempDir: Path) {
    val provider = provider(
      tempDir,
      libraries = mapOf(
        "demo-lib" to BazelTargetsInfo.LibraryDescription(
          target = "@lib//:demo-lib",
          jars = listOf("external/lib+/demo-lib-1.0.jar"),
          jarTargets = listOf("@lib//:demo-lib-1.0.jar"),
          sourceJars = emptyList(),
        ),
      ),
    )

    assertThatThrownBy { provider.findLibraryRoots("demo-lib", moduleLibraryModuleName = null) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageStartingWith("1 jar of library 'demo-lib' (project level) does not exist:\n")
      .hasMessageContaining(
        "  @lib//:demo-lib-1.0.jar (library 'demo-lib' (project level)): ${tempDir.resolve("output-base/external/lib+/demo-lib-1.0.jar")}\n"
      )
      .hasMessageEndingWith("Build it: ./bazel.cmd build @lib//:demo-lib-1.0.jar")
  }

  @Test
  fun `a long list is cut, and the command reads the labels from a file`(@TempDir tempDir: Path) {
    val missing = MissingBazelOutputs(BazelOutputMode.OUTSIDE_BAZEL)
    val count = MAX_LISTED_MISSING_JARS + 10
    for (i in 0 until count) {
      val file = tempDir.resolve("m$i.jar")
      assertThat(missing.takeIfPresent(BazelOutputJar(label = "@community//m$i:m$i.jar", file = file), owner = "module 'm$i'")).isNull()
    }

    val labelFileDirectory = tempDir.resolve("temp")
    assertThatThrownBy {
      missing.throwIfAny(subject = "the runtime classpath of module 'm0'", labelFileDirectory = labelFileDirectory, starterHint = true)
    }
      .hasMessageStartingWith("$count jars of the runtime classpath of module 'm0' do not exist:\n")
      .hasMessageContaining("  ... and 10 more\n")
      .hasMessageContaining("Build them: ./bazel.cmd build --target_pattern_file=$labelFileDirectory")
      .hasMessageNotContaining("@community//m${count - 1}:")
      .hasMessageContaining("-Didea.ide.config.path=")

    val labelFile = Files.list(labelFileDirectory).use { it.toList() }.single()
    assertThat(Files.readAllLines(labelFile))
      .hasSize(count)
      .startsWith("@community//m0:m0.jar")
      .endsWith("@community//m${count - 1}:m${count - 1}.jar")
  }

  @Test
  fun `under runfiles the error says to declare the labels in data`() {
    val missing = MissingBazelOutputs(BazelOutputMode.RUNFILES)
    val detail = "Unable to find dependency '@community//demo:demo.jar' at /runfiles/demo.jar\n(RUNFILES_MANIFEST_ONLY=null)\n"
    val jar = BazelOutputJar(label = "@community//demo:demo.jar", file = null, detail = detail)
    missing.takeIfPresent(jar, owner = "module 'intellij.demo'")
    missing.takeIfPresent(BazelOutputJar(label = "@lib//:demo-lib-1.0.jar", file = null), owner = "library 'demo-lib' (project level)")

    assertThatThrownBy { missing.throwIfAny(subject = "the runtime classpath of module 'intellij.demo'") }
      .hasMessage(
        """
        |2 jars of the runtime classpath of module 'intellij.demo' are not in the Bazel runfiles:
        |  @community//demo:demo.jar (module 'intellij.demo')
        |    Unable to find dependency '@community//demo:demo.jar' at /runfiles/demo.jar
        |    (RUNFILES_MANIFEST_ONLY=null)
        |  @lib//:demo-lib-1.0.jar (library 'demo-lib' (project level))
        |Declare these labels in the `data` of the Bazel target that runs this build. `./bazel.cmd build` does not help, because a Bazel target reads only the files that it declares.
        """.trimMargin()
      )
  }

  @Test
  fun `a jar that two modules ask for is listed once`(@TempDir tempDir: Path) {
    val missing = MissingBazelOutputs(BazelOutputMode.OUTSIDE_BAZEL)
    val jar = BazelOutputJar(label = "@community//demo:demo.jar", file = tempDir.resolve("demo.jar"))
    missing.takeIfPresent(jar, owner = "module 'intellij.demo'")
    missing.takeIfPresent(jar, owner = "module 'intellij.demo' (tests)")

    assertThatThrownBy { missing.throwIfAny(subject = "module 'intellij.demo'") }
      .hasMessageStartingWith("1 jar of module 'intellij.demo' does not exist:\n")
  }

  @Test
  fun `one container label with two absent files lists both`(@TempDir tempDir: Path) {
    val missing = MissingBazelOutputs(BazelOutputMode.OUTSIDE_BAZEL)
    val owner = "library 'demo-lib' (project level)"
    missing.takeIfPresent(BazelOutputJar(label = "@lib//:demo-lib", file = tempDir.resolve("a.jar")), owner = owner)
    missing.takeIfPresent(BazelOutputJar(label = "@lib//:demo-lib", file = tempDir.resolve("b.jar")), owner = owner)
    missing.takeIfPresent(BazelOutputJar(label = null, file = null), owner = owner)
    missing.takeIfPresent(BazelOutputJar(label = null, file = null), owner = owner)

    assertThatThrownBy { missing.throwIfAny(subject = owner) }
      .hasMessageStartingWith("4 jars of $owner do not exist:\n")
      .hasMessageContaining("  @lib//:demo-lib ($owner): ${tempDir.resolve("a.jar")}\n")
      .hasMessageContaining("  @lib//:demo-lib ($owner): ${tempDir.resolve("b.jar")}\n")
      .hasMessageEndingWith("Build them: ./bazel.cmd build @lib//:demo-lib")
  }

  @Test
  fun `no absent jar is no error`() {
    MissingBazelOutputs(BazelOutputMode.RUNFILES).throwIfAny(subject = "module 'intellij.demo'")
  }
}

private fun provider(
  tempDir: Path,
  modules: Map<String, BazelTargetsInfo.TargetsFileModuleDescription> = emptyMap(),
  libraries: Map<String, BazelTargetsInfo.LibraryDescription> = emptyMap(),
): BazelModuleOutputProvider {
  val state = BazelModuleOutputProviderState(
    modules = emptyList(),
    projectHome = tempDir,
    bazelOutputRootResolver = { tempDir.resolve("output-base") },
    bazelTargetsLoader = {
      BazelTargetsInfo.TargetsFile(modules = modules, projectLibraries = libraries, pluginDistributionTargets = emptyMap())
    },
    outputMode = BazelOutputMode.OUTSIDE_BAZEL,
  )
  return BazelModuleOutputProvider(state = state, lifetime = null, useTestCompilationOutput = false)
}
