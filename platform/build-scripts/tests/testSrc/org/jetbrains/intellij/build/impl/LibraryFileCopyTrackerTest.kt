// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class LibraryFileCopyTrackerTest {
  @Test
  fun `a library whose every jar another library packed into the same target is not resolved`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.sample", JpsJavaModuleType.INSTANCE)
    val engine = module.libraryCollection.addLibrary("engine", JpsJavaLibraryType.INSTANCE)
    val detect = module.libraryCollection.addLibrary("detect", JpsJavaLibraryType.INSTANCE)
    val engineJar = Path.of("/maven/engine-1.jar")
    val detectJar = Path.of("/maven/detect-1.jar")
    val provider = IdentityOutputProvider(
      module = module,
      identities = mapOf("engine" to listOf("@lib//:engine-1.jar", "@lib//:detect-1.jar"), "detect" to listOf("@lib//:detect-1.jar")),
      roots = mapOf("engine" to listOf(engineJar, detectJar), "detect" to listOf(detectJar)),
    )
    val moduleJar = Path.of("/dist/plugins/sample/lib/sample.jar")
    val otherJar = Path.of("/dist/plugins/sample/lib/other.jar")
    val tracker = LibraryFileCopyTracker()

    assertThat(tracker.getLibraryFiles(library = engine, targetFile = moduleJar, outputProvider = provider)).containsExactly(engineJar, detectJar)
    assertThat(tracker.getLibraryFiles(library = detect, targetFile = moduleJar, outputProvider = provider)).isEmpty()
    // Another target still takes the file, and only then is the library resolved.
    assertThat(tracker.getLibraryFiles(library = detect, targetFile = otherJar, outputProvider = provider)).containsExactly(detectJar)
    assertThat(provider.resolved).containsExactly("engine", "detect")
  }

  @Test
  fun `a library that shares one jar is resolved and contributes the rest`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.sample", JpsJavaModuleType.INSTANCE)
    val first = module.libraryCollection.addLibrary("first", JpsJavaLibraryType.INSTANCE)
    val second = module.libraryCollection.addLibrary("second", JpsJavaLibraryType.INSTANCE)
    val shared = Path.of("/maven/shared-1.jar")
    val own = Path.of("/maven/own-1.jar")
    val provider = IdentityOutputProvider(
      module = module,
      identities = mapOf("first" to listOf("@lib//:shared-1.jar"), "second" to listOf("@lib//:shared-1.jar", "@lib//:own-1.jar")),
      roots = mapOf("first" to listOf(shared), "second" to listOf(shared, own)),
    )
    val moduleJar = Path.of("/dist/plugins/sample/lib/sample.jar")
    val tracker = LibraryFileCopyTracker()

    assertThat(tracker.getLibraryFiles(library = first, targetFile = moduleJar, outputProvider = provider)).containsExactly(shared)
    assertThat(tracker.getLibraryFiles(library = second, targetFile = moduleJar, outputProvider = provider)).containsExactly(own)
  }

  @Test
  fun `tracks library copies by final target file`() {
    val tracker = LibraryFileCopyTracker()
    val file = Path.of("/maven/io/mockk/mockk-agent/1.14.5/mockk-agent-1.14.5.jar")
    val moduleJar = Path.of("/dist/plugins/python-junit5Tests-plugin/lib/modules/intellij.python.pyproject.tests.jar")
    val separateJar = Path.of("/dist/plugins/python-junit5Tests-plugin/lib/mockk-agent.jar")
    val anotherSeparateJar = Path.of("/dist/plugins/another-plugin/lib/mockk-agent.jar")

    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = moduleJar)).isTrue()
    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = separateJar)).isTrue()
    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = separateJar)).isFalse()
    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = anotherSeparateJar)).isTrue()
  }

  @Test
  fun `normalizes library jar file names`() {
    assertThat(removeVersionFromJar("mockk-agent-1.14.5.jar")).isEqualTo("mockk-agent.jar")
    assertThat(removeVersionFromJar("maven-resolver-provider.jar")).isEqualTo("maven-resolver-provider.jar")
    assertThat(nameToJarFileName("io.mockk agent")).isEqualTo("io.mockk-agent.jar")
  }

  @Test
  fun `detects jars packed separately`() {
    assertThat(isSeparateLibraryJar("mockk-agent-1.14.5.jar")).isTrue()
    assertThat(isSeparateLibraryJar("byte-buddy-1.17.7.jar")).isTrue()
    assertThat(isSeparateLibraryJar("kotlin-reflect-rt.jar")).isTrue()
    assertThat(isSeparateLibraryJar("maven-resolver-provider.jar")).isTrue()
    assertThat(isSeparateLibraryJar("code-agents-agent.jar")).isFalse()
    assertThat(isSeparateLibraryJar("kotlin-stdlib.jar")).isFalse()
    // an ordinary library: it must stay inside the content module that wraps it, or that module's jar ends up empty
    assertThat(isSeparateLibraryJar("objenesis-3.4.jar")).isFalse()
    // API clients, not Java agents: hoisting them splits them from the library they call across two class loaders
    assertThat(isSeparateLibraryJar("jcp-agent-spawner-sessions-api-0.3.1.jar")).isFalse()
    assertThat(isSeparateLibraryJar("jcp-agent-spawner-tasks-api-0.3.1.jar")).isFalse()
  }
}

/** Answers identities from a table and records which libraries a caller resolved. */
private class IdentityOutputProvider(
  private val module: JpsModule,
  private val identities: Map<String, List<String>>,
  private val roots: Map<String, List<Path>>,
) : ModuleOutputProvider {
  @JvmField val resolved: MutableList<String> = ArrayList()

  override val useTestCompilationOutput: Boolean = false

  override fun findModule(name: String): JpsModule? = module.takeIf { it.name == name }

  override fun findRequiredModule(name: String): JpsModule = requireNotNull(findModule(name))

  override fun getModuleImlFile(module: JpsModule): Path = error("Not needed")

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean): Path? = null

  override fun getLibraryJarIdentities(libraryName: String, moduleLibraryModuleName: String?): List<String> {
    assertThat(moduleLibraryModuleName).isEqualTo(module.name)
    return identities.getValue(libraryName)
  }

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    resolved.add(libraryName)
    return roots.getValue(libraryName)
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = emptyList()

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? = null
}
