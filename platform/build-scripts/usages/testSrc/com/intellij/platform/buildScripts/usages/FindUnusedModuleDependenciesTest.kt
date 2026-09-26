// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.usages

import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder
import org.jetbrains.jps.dependency.impl.PathSource
import org.jetbrains.jps.dependency.impl.PathSourceMapper
import org.jetbrains.jps.dependency.java.ClassUsage
import org.jetbrains.jps.dependency.java.FileNode
import org.jetbrains.jps.dependency.java.ImportPackageOnDemandUsage
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.writeText

class FindUnusedModuleDependenciesTest {
  @Test
  fun `reports removable compile dependency`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val used = fixture.module("used")
    val unused = fixture.module("unused")
    fixture.addDependency(current, used)
    fixture.addDependency(current, unused)

    fixture.writeModuleJars(used, prodClasses = listOf("used/Api"))
    fixture.writeModuleJars(unused, prodClasses = listOf("unused/Api"))
    fixture.writeTargetData(
      current,
      productionUsages = listOf(ClassUsage("used/Api")),
      productionClasspath = listOf(fixture.prodAbiJar(used), fixture.prodAbiJar(unused)),
    )

    val report = fixture.analyze("current")
    assertTrue(report.failures.isEmpty())
    assertEquals(listOf(RemovableDependency("unused", JpsJavaDependencyScope.COMPILE)), report.moduleResults.single().removableDependencies)
  }

  @Test
  fun `reports removable test dependency`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val testOnly = fixture.module("testOnly")
    fixture.addDependency(current, testOnly, scope = JpsJavaDependencyScope.TEST)

    fixture.writeModuleJars(testOnly, prodClasses = listOf("testOnly/Api"))
    fixture.writeTargetData(
      current,
      productionUsages = emptyList(),
      productionClasspath = listOf(fixture.prodAbiJar(testOnly)),
      testUsages = emptyList(),
      testClasspath = listOf(fixture.prodAbiJar(testOnly)),
    )

    val report = fixture.analyze("current")
    assertTrue(report.failures.isEmpty())
    assertEquals(listOf(RemovableDependency("testOnly", JpsJavaDependencyScope.TEST)), report.moduleResults.single().removableDependencies)
  }

  @Test
  fun `keeps compile dependency used only from tests`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val dep = fixture.module("dep")
    fixture.addDependency(current, dep)

    fixture.writeModuleJars(dep, prodClasses = listOf("dep/Api"))
    fixture.writeTargetData(
      current,
      productionUsages = emptyList(),
      productionClasspath = listOf(fixture.prodAbiJar(dep)),
      testUsages = listOf(ClassUsage("dep/Api")),
      testClasspath = listOf(fixture.prodAbiJar(dep)),
    )

    val report = fixture.analyze("current")
    assertTrue(report.failures.isEmpty())
    assertTrue(report.moduleResults.single().removableDependencies.isEmpty())
  }

  @Test
  fun `keeps direct dependency needed through exported closure`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val bridge = fixture.module("bridge")
    val exported = fixture.module("exported")
    fixture.addDependency(current, bridge)
    fixture.addDependency(bridge, exported, exported = true)

    fixture.writeModuleJars(bridge, prodClasses = listOf("bridge/Api"))
    fixture.writeModuleJars(exported, prodClasses = listOf("exported/Api"))
    fixture.writeTargetData(
      current,
      productionUsages = listOf(ClassUsage("exported/Api")),
      productionClasspath = listOf(fixture.prodAbiJar(bridge), fixture.prodAbiJar(exported)),
    )

    val report = fixture.analyze("current")
    assertTrue(report.failures.isEmpty())
    assertTrue(report.moduleResults.single().removableDependencies.isEmpty())
  }

  @Test
  fun `prefers earlier non-module classpath owner over later module jar`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val dep = fixture.module("dep")
    fixture.addDependency(current, dep)

    val externalAbi = tempDir.resolve("external/external.abi.jar")
    writeJar(externalAbi, listOf("duplicate/Api"))
    fixture.writeModuleJars(dep, prodClasses = listOf("duplicate/Api"))
    fixture.writeTargetData(
      current,
      productionUsages = listOf(ClassUsage("duplicate/Api")),
      productionClasspath = listOf(externalAbi, fixture.prodAbiJar(dep)),
    )

    val report = fixture.analyze("current")
    assertTrue(report.failures.isEmpty())
    assertEquals(listOf(RemovableDependency("dep", JpsJavaDependencyScope.COMPILE)), report.moduleResults.single().removableDependencies)
  }

  @Test
  fun `resolves package-owned usages through classpath jars`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val dep = fixture.module("dep")
    fixture.addDependency(current, dep)

    fixture.writeModuleJars(dep, prodClasses = listOf("pkg/Foo"))
    fixture.writeTargetData(
      current,
      productionUsages = listOf(ImportPackageOnDemandUsage("pkg")),
      productionClasspath = listOf(fixture.prodAbiJar(dep)),
    )

    val report = fixture.analyze("current")
    assertTrue(report.failures.isEmpty())
    assertTrue(report.moduleResults.single().removableDependencies.isEmpty())
  }

  @Test
  fun `marks module as not analyzed when IC data is missing`(@TempDir tempDir: Path) {
    val fixture = AnalyzerTestFixture(tempDir)
    val current = fixture.module("current")
    val dep = fixture.module("dep")
    fixture.addDependency(current, dep)

    fixture.writeModuleJars(dep, prodClasses = listOf("dep/Api"))
    fixture.writeModuleJars(current, prodClasses = listOf("current/Api"))

    val report = fixture.analyze("current")
    assertEquals(2, report.exitCode)
    assertEquals(listOf(ModuleAnalysisFailure("current", "missing or unreadable production IC data")), report.failures)
  }

  private class AnalyzerTestFixture(private val projectRoot: Path) {
    private val jpsProject: JpsProject = JpsElementFactory.getInstance().createModel().project
    private val modules = LinkedHashMap<String, ModuleFiles>()
    private val pathMapper = createProjectRootPathMapper(projectRoot)

    fun module(name: String): JpsModule {
      return jpsProject.addModule(name, JpsJavaModuleType.INSTANCE).also {
        modules[name] = ModuleFiles(projectRoot, name)
      }
    }

    fun addDependency(from: JpsModule, to: JpsModule, scope: JpsJavaDependencyScope = JpsJavaDependencyScope.COMPILE, exported: Boolean = false) {
      val dependency = from.dependenciesList.addModuleDependency(to)
      JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).apply {
        this.scope = scope
        isExported = exported
      }
    }

    fun prodAbiJar(module: JpsModule): Path = moduleFiles(module).prodAbiJar

    fun writeModuleJars(module: JpsModule, prodClasses: List<String> = emptyList(), testClasses: List<String> = emptyList()) {
      val files = moduleFiles(module)
      writeJar(files.prodJar, prodClasses)
      writeJar(files.prodAbiJar, prodClasses)
      if (testClasses.isNotEmpty()) {
        writeJar(files.testJar, testClasses)
        writeJar(files.testAbiJar, testClasses)
      }
      writeTargetsFile()
    }

    fun writeTargetData(
      module: JpsModule,
      productionUsages: List<Usage>,
      productionClasspath: List<Path>,
      testUsages: List<Usage>? = null,
      testClasspath: List<Path>? = null,
    ) {
      val files = moduleFiles(module)
      writeJar(files.prodJar, emptyList())
      writeDependencyGraph(files.prodGraphFile, productionUsages)
      writeConfigState(files.prodDataDir.resolve(IcDataPaths.CONFIG_STATE_FILE_NAME), productionClasspath, pathMapper)

      writeJar(files.testJar, emptyList())
      writeDependencyGraph(files.testGraphFile, testUsages.orEmpty())
      writeConfigState(files.testDataDir.resolve(IcDataPaths.CONFIG_STATE_FILE_NAME), testClasspath.orEmpty(), pathMapper)

      writeTargetsFile()
    }

    fun analyze(vararg moduleNames: String): AnalysisReport {
      writeTargetsFile()
      return UnusedModuleDependencyAnalyzer(projectRoot).analyze(
        jpsProject,
        CliOptions(projectRoot = projectRoot, analyzeAll = false, moduleNames = moduleNames.toSet()),
      )
    }

    private fun moduleFiles(module: JpsModule): ModuleFiles = modules.getValue(module.name)

    private fun writeTargetsFile() {
      val content = buildString {
        appendLine("{")
        appendLine("  \"modules\": {")
        modules.entries.sortedBy { it.key }.forEachIndexed { index, (moduleName, files) ->
          appendLine("    \"$moduleName\": {")
          appendLine("      \"productionTargets\": [\"//$moduleName:${files.prodJar.name}\"],")
          appendLine("      \"productionJars\": [\"${projectRoot.relativize(files.prodJar).invariantSeparatorsPathString}\"],")
          appendLine("      \"testTargets\": [\"//$moduleName:${files.testJar.name}\"],")
          appendLine("      \"testJars\": [\"${projectRoot.relativize(files.testJar).invariantSeparatorsPathString}\"]")
          append("    }")
          appendLine(if (index == modules.size - 1) "" else ",")
        }
        appendLine("  }")
        appendLine("}")
      }
      val targetsFile = projectRoot.resolve("build/bazel-targets.json")
      targetsFile.parent.createDirectories()
      targetsFile.writeText(content)
    }
  }

  private data class ModuleFiles(
    val prodJar: Path,
    val prodAbiJar: Path,
    val prodDataDir: Path,
    val prodGraphFile: Path,
    val testJar: Path,
    val testAbiJar: Path,
    val testDataDir: Path,
    val testGraphFile: Path,
  ) {
    constructor(projectRoot: Path, moduleName: String) : this(
      prodJar = projectRoot.resolve("out/$moduleName/$moduleName.jar"),
      prodAbiJar = projectRoot.resolve("out/$moduleName/$moduleName.abi.jar"),
      prodDataDir = projectRoot.resolve("out/$moduleName/${moduleName}-ic"),
      prodGraphFile = projectRoot.resolve("out/$moduleName/${moduleName}-ic/${IcDataPaths.DEP_GRAPH_FILE_NAME}"),
      testJar = projectRoot.resolve("out/$moduleName/${moduleName}_test.jar"),
      testAbiJar = projectRoot.resolve("out/$moduleName/${moduleName}_test.abi.jar"),
      testDataDir = projectRoot.resolve("out/$moduleName/${moduleName}_test-ic"),
      testGraphFile = projectRoot.resolve("out/$moduleName/${moduleName}_test-ic/${IcDataPaths.DEP_GRAPH_FILE_NAME}"),
    )
  }
}

private fun writeDependencyGraph(graphFile: Path, usages: List<Usage>) {
  graphFile.parent.createDirectories()
  DependencyGraphImpl(IcPersistentMVStoreMapletFactory(graphFile.toString(), 1)).useGraph { graph ->
    val source = PathSource("src/Main.kt")
    val delta = graph.createDelta(listOf(source), emptyList(), false)
    delta.associate(FileNode("src/Main.kt", usages), listOf(source))
    graph.integrate(graph.differentiate(delta, DifferentiateParametersBuilder.create().calculateAffected(false).get()))
  }
}

private fun writeConfigState(
  configFile: Path,
  classpathEntries: List<Path>,
  pathMapper: PathSourceMapper,
) {
  IcConfigurationStateIO.writeConfigState(configFile, classpathEntries, pathMapper)
}

private fun writeJar(path: Path, classNames: Collection<String>) {
  path.parent.createDirectories()
  ZipOutputStream(Files.newOutputStream(path)).use { zip ->
    for (className in classNames.sorted()) {
      zip.putNextEntry(ZipEntry("$className.class"))
      zip.write(createPublicClass(className))
      zip.closeEntry()
    }
  }
}

private fun createPublicClass(className: String): ByteArray {
  val writer = ClassWriter(0)
  writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, className, null, "java/lang/Object", null)

  val constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
  constructor.visitCode()
  constructor.visitVarInsn(Opcodes.ALOAD, 0)
  constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
  constructor.visitInsn(Opcodes.RETURN)
  constructor.visitMaxs(1, 1)
  constructor.visitEnd()

  writer.visitEnd()
  return writer.toByteArray()
}
