// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JpsCompilationData
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

internal class BazelCompilationContextTest {
  @Test
  fun `createCopy reuses Bazel targets metadata across providers`(@TempDir tempDir: Path) {
    val moduleName = "intellij.test.module"
    val module = mock(JpsModule::class.java)
    `when`(module.name).thenReturn(moduleName)

    val project = mock(JpsProject::class.java)
    `when`(project.modules).thenReturn(listOf(module))

    val loadCounter = AtomicInteger()
    val state = BazelModuleOutputProviderState(
      modules = project.modules,
      projectHome = tempDir,
      bazelOutputRoot = tempDir,
      bazelTargetsLoader = {
        loadCounter.incrementAndGet()
        BazelTargetsInfo.TargetsFile(
          modules = mapOf(
            moduleName to BazelTargetsInfo.TargetsFileModuleDescription(
              productionTargets = emptyList(),
              productionJars = emptyList(),
              testTargets = emptyList(),
              testJars = emptyList(),
              exports = emptyList(),
              moduleLibraries = emptyMap(),
            ),
          ),
          projectLibraries = emptyMap(),
        )
      },
    )

    val baseContext = BazelCompilationContext(
      delegate = testCompilationContext(project = project, tempDir = tempDir, options = BuildOptions()),
      scope = null,
      outputProviderState = state,
    )
    val productionCopy = baseContext.createCopy(
      messages = mock(BuildMessages::class.java),
      options = BuildOptions(),
      paths = buildPaths(tempDir.resolve("copy-out-production"), tempDir.resolve("copy-project-production")),
    ) as BazelCompilationContext
    val testCopy = baseContext.createCopy(
      messages = mock(BuildMessages::class.java),
      options = BuildOptions(useTestCompilationOutput = true),
      paths = buildPaths(tempDir.resolve("copy-out-tests"), tempDir.resolve("copy-project-tests")),
    ) as BazelCompilationContext

    val baseProvider = baseContext.outputProvider
    val productionProvider = productionCopy.outputProvider
    val testProvider = testCopy.outputProvider

    assertThat(baseProvider).isNotSameAs(productionProvider)
    assertThat(baseProvider).isNotSameAs(testProvider)
    assertThat(baseContext.outputProviderState).isSameAs(productionCopy.outputProviderState)
    assertThat(baseContext.outputProviderState).isSameAs(testCopy.outputProviderState)

    baseContext.outputProviderState.bazelTargetsMap
    productionCopy.outputProviderState.bazelTargetsMap
    testCopy.outputProviderState.bazelTargetsMap

    assertThat(loadCounter.get()).isEqualTo(1)
    assertThat(baseProvider.useTestCompilationOutput).isFalse()
    assertThat(productionProvider.useTestCompilationOutput).isFalse()
    assertThat(testProvider.useTestCompilationOutput).isTrue()
  }

  private fun testCompilationContext(project: JpsProject, tempDir: Path, options: BuildOptions): CompilationContext {
    val paths = buildPaths(tempDir.resolve("out"), tempDir.resolve("project"))
    val stableJavaExecutable = tempDir.resolve("jdk/bin/java")
    Files.createDirectories(stableJavaExecutable.parent)
    val compilationData = JpsCompilationData(
      dataStorageRoot = tempDir.resolve("data-storage"),
      classesOutputDirectory = tempDir.resolve("classes"),
      buildLogFile = tempDir.resolve("compilation.log"),
      categoriesWithDebugLevel = "",
    )
    return TestCompilationContext(
      messages = mock(BuildMessages::class.java),
      options = options,
      paths = paths,
      project = project,
      projectModel = mock(JpsModel::class.java),
      dependenciesProperties = DependenciesProperties(BuildPaths.COMMUNITY_ROOT),
      bundledRuntime = mock(BundledRuntime::class.java),
      compilationData = compilationData,
      stableJavaExecutable = stableJavaExecutable,
      classesOutputDirectory = tempDir.resolve("classes"),
    )
  }

  private fun buildPaths(buildOutputDir: Path, projectHome: Path): BuildPaths {
    val tempDir = buildOutputDir.resolve("temp")
    Files.createDirectories(tempDir)
    return BuildPaths(
      communityHomeDirRoot = BuildPaths.COMMUNITY_ROOT,
      buildOutputDir = buildOutputDir,
      logDir = buildOutputDir.resolve("log"),
      projectHome = projectHome,
      artifactDir = buildOutputDir.resolve("artifacts"),
      tempDir = tempDir,
    )
  }

  private class TestCompilationContext(
    override val messages: BuildMessages,
    override val options: BuildOptions,
    override val paths: BuildPaths,
    override val project: JpsProject,
    override val projectModel: JpsModel,
    override val dependenciesProperties: DependenciesProperties,
    override val bundledRuntime: BundledRuntime,
    override val compilationData: JpsCompilationData,
    override val stableJavaExecutable: Path,
    override val classesOutputDirectory: Path,
  ) : CompilationContext {
    override val outputProvider: ModuleOutputProvider
      get() = error("Test delegate output provider should not be used")

    override suspend fun getStableJdkHome(): Path = stableJavaExecutable.parent.parent

    override suspend fun getOriginalModuleRepository(): OriginalModuleRepository = mock(OriginalModuleRepository::class.java)

    override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): Collection<Path> = emptyList()

    override fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean): Path? = null

    override fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean): Path? = null

    override fun notifyArtifactBuilt(artifactPath: Path) = Unit

    override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths, scope: CoroutineScope?): CompilationContext {
      return TestCompilationContext(
        messages = messages,
        options = options,
        paths = paths,
        project = project,
        projectModel = projectModel,
        dependenciesProperties = dependenciesProperties,
        bundledRuntime = bundledRuntime,
        compilationData = compilationData,
        stableJavaExecutable = stableJavaExecutable,
        classesOutputDirectory = classesOutputDirectory,
      )
    }

    override suspend fun prepareForBuild() = Unit

    override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) = Unit

    override suspend fun withCompilationLock(block: suspend () -> Unit) = block()
  }
}
