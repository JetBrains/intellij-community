// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class ArchivedCompilationContextTest {
  @Test
  fun `createCopy enables archived output zip cache when scope is provided`(@TempDir tempDir: Path) {
    runBlocking {
      val moduleName = "intellij.test.module"
      val module = mock(JpsModule::class.java)
      `when`(module.name).thenReturn(moduleName)

      val project = mock(JpsProject::class.java)
      `when`(project.modules).thenReturn(listOf(module))

      val classesOutputDirectory = tempDir.resolve("classes")
      val moduleOutputRoot = classesOutputDirectory.resolve("production").resolve(moduleName)
      Files.createDirectories(moduleOutputRoot)

      val archivedOutputDirectory = tempDir.resolve("archives")
      val archivedModuleOutput = archivedOutputDirectory.resolve("production").resolve(moduleName).resolve("module-output.jar")
      createArchiveWithPluginXml(archivedModuleOutput)

      val baseContext = createArchivedCompilationContext(
        delegate = testCompilationContext(
          project = project,
          tempDir = tempDir,
          outputProvider = TestModuleOutputProvider(module = module, moduleOutputRoot = moduleOutputRoot, imlFile = tempDir.resolve("$moduleName.iml")),
        ),
        storagePaths = buildPaths(buildOutputDir = tempDir.resolve("base-out"), projectHome = tempDir.resolve("base-project")),
        classesOutputDirectory = classesOutputDirectory,
        archivedOutputDirectory = archivedOutputDirectory,
        archivedMapping = mapOf(moduleOutputRoot to archivedModuleOutput),
      )

      assertThat(isZipCacheEnabled(baseContext.outputProvider)).isFalse()

      supervisorScope {
        val copiedContext = baseContext.createCopy(
          messages = mock(BuildMessages::class.java),
          options = BuildOptions(),
          paths = buildPaths(buildOutputDir = tempDir.resolve("copy-out"), projectHome = tempDir.resolve("copy-project")),
          scope = this,
        )

        assertThat(isZipCacheEnabled(copiedContext.outputProvider)).isTrue()
        assertThat(copiedContext.outputProvider.getModuleOutputRoots(module, forTests = false)).containsExactly(archivedModuleOutput)
        assertThat(copiedContext.outputProvider.readFileContentFromModuleOutput(module, "META-INF/plugin.xml", forTests = false))
          .isEqualTo("<idea-plugin/>".encodeToByteArray())
      }
    }
  }

  private fun isZipCacheEnabled(outputProvider: ModuleOutputProvider): Boolean {
    val zipFilePoolField = outputProvider.javaClass.getDeclaredField("zipFilePool")
    zipFilePoolField.isAccessible = true
    val zipFilePool = zipFilePoolField.get(outputProvider)

    val cacheField = zipFilePool.javaClass.getDeclaredField("cache")
    cacheField.isAccessible = true
    return cacheField.get(zipFilePool) != null
  }

  private fun createArchiveWithPluginXml(archivePath: Path) {
    Files.createDirectories(archivePath.parent)
    ZipOutputStream(Files.newOutputStream(archivePath)).use { output ->
      output.putNextEntry(ZipEntry("META-INF/plugin.xml"))
      output.write("<idea-plugin/>".encodeToByteArray())
      output.closeEntry()
    }
  }

  private fun createArchivedCompilationContext(
    delegate: CompilationContext,
    storagePaths: BuildPaths,
    classesOutputDirectory: Path,
    archivedOutputDirectory: Path,
    archivedMapping: Map<Path, Path>,
  ): CompilationContext {
    val storageClass = Class.forName("org.jetbrains.intellij.build.impl.compilation.ArchivedCompilationOutputStorage")
    val storageConstructor = storageClass.getDeclaredConstructor(
      BuildPaths::class.java,
      Path::class.java,
      Path::class.java,
      Map::class.java,
      Boolean::class.javaPrimitiveType,
    )
    storageConstructor.isAccessible = true
    val storage = storageConstructor.newInstance(storagePaths, classesOutputDirectory, archivedOutputDirectory, archivedMapping, false)

    val archivedContextClass = Class.forName("org.jetbrains.intellij.build.impl.ArchivedCompilationContext")
    val archivedContextConstructor = archivedContextClass.getDeclaredConstructor(
      CompilationContext::class.java,
      storageClass,
      CoroutineScope::class.java,
    )
    archivedContextConstructor.isAccessible = true
    return archivedContextConstructor.newInstance(delegate, storage, null) as CompilationContext
  }

  private fun testCompilationContext(
    project: JpsProject,
    tempDir: Path,
    outputProvider: ModuleOutputProvider,
    options: BuildOptions = BuildOptions(),
    classesOutputDirectory: Path = tempDir.resolve("classes"),
  ): CompilationContext {
    val paths = buildPaths(buildOutputDir = tempDir.resolve("delegate-out"), projectHome = tempDir.resolve("delegate-project"))
    val stableJavaExecutable = tempDir.resolve("jdk/bin/java")
    Files.createDirectories(stableJavaExecutable.parent)
    val compilationData = JpsCompilationData(
      dataStorageRoot = tempDir.resolve("data-storage"),
      classesOutputDirectory = classesOutputDirectory,
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
      classesOutputDirectory = classesOutputDirectory,
      outputProvider = outputProvider,
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
    override val outputProvider: ModuleOutputProvider,
  ) : CompilationContext {
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
        outputProvider = outputProvider,
      )
    }

    override suspend fun prepareForBuild() = Unit

    override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) = Unit

    override suspend fun withCompilationLock(block: suspend () -> Unit) = block()
  }

  private class TestModuleOutputProvider(
    private val module: JpsModule,
    private val moduleOutputRoot: Path,
    private val imlFile: Path,
  ) : ModuleOutputProvider {
    override val useTestCompilationOutput: Boolean = false

    override fun getAllModules(): List<JpsModule> = listOf(module)

    override fun findModule(name: String): JpsModule? = module.takeIf { it.name == name }

    override fun getModuleImlFile(module: JpsModule): Path = imlFile

    override fun findRequiredModule(name: String): JpsModule {
      return requireNotNull(findModule(name)) { "Unknown module $name" }
    }

    override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> = emptyList()

    override fun getProjectLibraryToModuleMap(): Map<String, String> = emptyMap()

    override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
      return if (!forTests && module == this.module) listOf(moduleOutputRoot) else emptyList()
    }

    override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? = null
  }
}
