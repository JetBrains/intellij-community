// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.DescriptorDependencyWalk
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.resolveDescriptor
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val DESCRIPTOR_PATH = "META-INF/shared.xml"

@ExtendWith(TestFailureLogger::class)
class DescriptorResolutionTest {
  @Test
  fun `own production source wins over a dependency`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        resourceRoot = "resources"
        moduleDep("intellij.b")
      }
      module("intellij.b") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.a", "own")
    writeResource(tempDir, "intellij.b", "dependency")

    assertThat(resolve(jps.project, TestOutputProvider(jps.project), "intellij.a")).isEqualTo("own")
  }

  @Test
  fun `a dependency source wins over the own module output`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        resourceRoot = "resources"
        moduleDep("intellij.b")
      }
      module("intellij.b") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.b", "dependency source")
    val provider = TestOutputProvider(jps.project, productionOutput = mapOf("intellij.a" to "own output"))

    assertThat(resolve(jps.project, provider, "intellij.a")).isEqualTo("dependency source")
  }

  @Test
  fun `a test resource root does not answer the sources pass`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        resourceRoot = "resources"
      }
    }
    val testRoot = tempDir.resolve("intellij/a/testResources")
    Files.createDirectories(testRoot.resolve(DESCRIPTOR_PATH).parent)
    Files.writeString(testRoot.resolve(DESCRIPTOR_PATH), "test descriptor")
    jps.project.modules.first { it.name == "intellij.a" }
      .addSourceRoot(JpsPathUtil.pathToUrl(testRoot.toString()), JavaResourceRootType.TEST_RESOURCE)

    assertThat(resolve(jps.project, TestOutputProvider(jps.project), "intellij.a")).isNull()
  }

  @Test
  fun `a test-only module answers from its test output`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a.tests")
    }
    val provider = TestOutputProvider(jps.project, testOutput = mapOf("intellij.a.tests" to "test output"))

    assertThat(resolve(jps.project, provider, "intellij.a.tests")).isEqualTo("test output")
  }

  @Test
  fun `the declared owner wins over the dependency walk`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        moduleDep("intellij.b")
      }
      module("intellij.b") {
        resourceRoot = "resources"
      }
      module("intellij.owner") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.b", "dependency")
    writeResource(tempDir, "intellij.owner", "owner")
    val provider = TestOutputProvider(jps.project)

    val data = runBlocking(Dispatchers.Default) {
      resolveDescriptor(
        module = provider.findRequiredModule("intellij.a"),
        path = DESCRIPTOR_PATH,
        outputProvider = provider,
        declaredOwner = provider.findRequiredModule("intellij.owner"),
      )
    }
    assertThat(data?.decodeToString()).isEqualTo("owner")
  }

  @Test
  fun `a library jar wins over any module output`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      library("shared-descriptor")
      module("intellij.a") {
        libraryDep("shared-descriptor")
      }
      module("intellij.z")
    }
    val jar = tempDir.resolve("shared-descriptor.jar")
    ZipOutputStream(Files.newOutputStream(jar)).use { out ->
      out.putNextEntry(ZipEntry(DESCRIPTOR_PATH))
      out.write("library jar".toByteArray())
      out.closeEntry()
    }
    val provider = TestOutputProvider(
      project = jps.project,
      productionOutput = mapOf("intellij.z" to "any module output"),
      libraryRoots = mapOf("shared-descriptor" to listOf(jar)),
    )

    assertThat(resolve(jps.project, provider, "intellij.a")).isEqualTo("library jar")
  }

  @Test
  fun `an include prefix keeps only the dependencies that match it`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        moduleDep("other.b")
        moduleDep("intellij.b")
      }
      module("other.b") {
        resourceRoot = "resources"
      }
      module("intellij.b") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "other.b", "other")
    writeResource(tempDir, "intellij.b", "intellij")
    val provider = TestOutputProvider(jps.project)

    assertThat(resolve(jps.project, provider, "intellij.a", DescriptorDependencyWalk(includePrefix = "intellij.")))
      .isEqualTo("intellij")
    assertThat(resolve(jps.project, provider, "intellij.a", DescriptorDependencyWalk(includePrefix = "nothing.")))
      .isNull()
  }

  @Test
  fun `an exclude recursion prefix reads the dependency but does not go into it`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        moduleDep("intellij.platform.core")
      }
      module("intellij.platform.core") {
        moduleDep("intellij.deep")
      }
      module("intellij.deep") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.deep", "deep")
    val provider = TestOutputProvider(jps.project)

    val walk = DescriptorDependencyWalk(excludeRecursionPrefix = "intellij.platform.")
    assertThat(resolve(jps.project, provider, "intellij.a", walk)).isNull()
    assertThat(resolve(jps.project, provider, "intellij.a")).isEqualTo("deep")
  }

  @Test
  fun `an exclude recursion prefix still goes into a dependency it does not match`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        moduleDep("intellij.other")
      }
      module("intellij.other") {
        moduleDep("intellij.deep")
      }
      module("intellij.deep") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.deep", "deep")
    val provider = TestOutputProvider(jps.project)

    val walk = DescriptorDependencyWalk(excludeRecursionPrefix = "intellij.platform.")
    assertThat(resolve(jps.project, provider, "intellij.a", walk)).isEqualTo("deep")
  }

  @Test
  fun `a walk that is not recursive reads the direct dependencies only`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        moduleDep("intellij.b")
      }
      module("intellij.b") {
        moduleDep("intellij.c")
      }
      module("intellij.c") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.c", "transitive")
    val provider = TestOutputProvider(jps.project)

    assertThat(resolve(jps.project, provider, "intellij.a", DescriptorDependencyWalk(recursive = false))).isNull()
    assertThat(resolve(jps.project, provider, "intellij.a")).isEqualTo("transitive")
  }

  @Test
  fun `a walk of null reads no dependency`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        moduleDep("intellij.b")
      }
      module("intellij.b") {
        resourceRoot = "resources"
      }
    }
    writeResource(tempDir, "intellij.b", "dependency")
    val provider = TestOutputProvider(jps.project)

    val data = runBlocking(Dispatchers.Default) {
      resolveDescriptor(
        module = provider.findRequiredModule("intellij.a"),
        path = DESCRIPTOR_PATH,
        outputProvider = provider,
        walk = null,
        searchAnyModuleOutput = false,
      )
    }
    assertThat(data).isNull()
  }

  @Test
  fun `a miss returns null`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.a") {
        resourceRoot = "resources"
      }
    }

    assertThat(resolve(jps.project, TestOutputProvider(jps.project), "intellij.a")).isNull()
  }

  private fun resolve(
    project: JpsProject,
    provider: ModuleOutputProvider,
    moduleName: String,
    walk: DescriptorDependencyWalk = DescriptorDependencyWalk(),
  ): String? {
    val module = project.modules.first { it.name == moduleName }
    return runBlocking(Dispatchers.Default) {
      resolveDescriptor(module = module, path = DESCRIPTOR_PATH, outputProvider = provider, walk = walk)
    }?.decodeToString()
  }

  private fun writeResource(baseDir: Path, moduleName: String, content: String) {
    val file = baseDir.resolve(moduleName.replace('.', '/')).resolve("resources").resolve(DESCRIPTOR_PATH)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
  }

}

/**
 * A [ModuleOutputProvider] whose module output and library jars the test states.
 *
 * The shared fixture provider refuses every output read, so it cannot show the order of the two search passes.
 */
private class TestOutputProvider(
  private val project: JpsProject,
  private val productionOutput: Map<String, String> = emptyMap(),
  private val testOutput: Map<String, String> = emptyMap(),
  private val libraryRoots: Map<String, List<Path>> = emptyMap(),
) : ModuleOutputProvider {
  override val useTestCompilationOutput: Boolean
    get() = true

  override fun findModule(name: String): JpsModule? = project.modules.find { it.name == name }

  override fun findRequiredModule(name: String): JpsModule = findModule(name) ?: error("Module not found: $name")

  override fun getAllModules(): List<JpsModule> = project.modules

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = emptyList()

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> =
    libraryRoots.get(libraryName) ?: emptyList()

  override fun getProjectLibraryToModuleMap(): Map<String, String> = emptyMap()

  override fun getModuleImlFile(module: JpsModule): Path = error("Not needed for this test")

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    if (relativePath != DESCRIPTOR_PATH) {
      return null
    }
    val content = if (forTests) testOutput.get(module.name) else productionOutput.get(module.name)
    return content?.toByteArray()
  }

  override fun findFileInAnyModuleOutput(
    relativePath: String,
    moduleNamePrefix: String?,
    processedModules: MutableSet<String>?,
  ): ByteArray? {
    if (relativePath != DESCRIPTOR_PATH) {
      return null
    }
    for (module in project.modules.sortedBy { it.name }) {
      if (moduleNamePrefix != null && !module.name.startsWith(moduleNamePrefix)) {
        continue
      }
      if (processedModules != null && !processedModules.add(module.name)) {
        continue
      }
      productionOutput.get(module.name)?.let { return it.toByteArray() }
    }
    return null
  }
}
