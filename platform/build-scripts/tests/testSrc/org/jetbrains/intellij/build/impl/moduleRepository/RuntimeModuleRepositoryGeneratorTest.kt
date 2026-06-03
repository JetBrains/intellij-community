// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleId.DEFAULT_NAMESPACE
import com.intellij.platform.runtime.repository.RuntimeModuleId.legacyJpsModule
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class RuntimeModuleRepositoryGeneratorTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  lateinit var project: JpsProject

  @BeforeEach
  fun setUp() {
    project = JpsElementFactory.getInstance().createModel().project
    project.container.setChild(JpsProjectSerializationDataExtensionImpl.ROLE, JpsProjectSerializationDataExtensionImpl(tempDirectory.rootPath.resolve("project")))
  }

  @Test
  fun `plugin with multiple modules`() {
    addModule("foo.plugin")
    val fooJps = addModule("foo.jps")
    addModule("foo.core", fooJps)
    addModule("foo.rt")
    val plugin = createHeader("foo.plugin", "foo.core")
    val distributionEntries = listOf(
      moduleOutput("foo.plugin"),
      moduleOutput("foo.core"),
      moduleOutput("foo.jps"),
      moduleOutput("foo.rt", "rt/foo.rt.jar"),
    )
    generateAndCheck(plugin, distributionEntries) {
      descriptor(legacyJpsModule("foo.plugin"), listOf("../lib/foo.plugin.jar"))
      descriptor(contentModule("foo.core"), listOf("../lib/foo.core.jar"), dependencies = listOf(legacyJpsModule("foo.jps")))
      descriptor(legacyJpsModule("foo.jps"), listOf("../lib/foo.jps.jar"))
      descriptor(legacyJpsModule("foo.rt"), listOf("../lib/rt/foo.rt.jar"))
      pluginHeader("com.foo.plugin", legacyJpsModule("foo.plugin"),
                   includedJpsModule("foo.plugin"), includedContentModule("foo.core"), includedJpsModule("foo.jps"),
      )
    }
  }

  @Test
  fun `plugin with module-level library`() {
    val foo = addModule("foo")
    val lib = foo.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    foo.dependenciesList.addLibraryDependency(lib)
    lib.addRoot(getUrl("project/lib"), JpsOrderRootType.COMPILED)
    val plugin = createHeader("foo")
    val distributionEntries = listOf(
      moduleOutput("foo"),
      moduleLibraryFileEntry("foo", "lib", tempDirectory.rootPath.resolve("lib/lib.jar"), "lib.jar"),
    )
    generateAndCheck(plugin, distributionEntries) {
      descriptor(legacyJpsModule("foo"),listOf("../lib/foo.jar", "../lib/lib.jar"), emptyList())
      pluginHeader("com.foo", legacyJpsModule("foo"), includedJpsModule("foo"))
    }
  }

  @Test
  fun `plugin with project-level library`() {
    val foo = addModule("foo")
    val lib = project.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    foo.dependenciesList.addLibraryDependency(lib)
    lib.addRoot(getUrl("project/lib"), JpsOrderRootType.COMPILED)
    val libId = RuntimeModuleId.projectLibrary("lib")
    val plugin = createHeader("foo")
    val distributionEntries = listOf(
      moduleOutput("foo"),
      projectLibraryEntry("lib", tempDirectory.rootPath.resolve("lib/lib.jar"), "lib.jar"),
    )
    generateAndCheck(plugin, distributionEntries) {
      descriptor(legacyJpsModule("foo"),listOf("../lib/foo.jar"), listOf(libId))
      descriptor(libId,listOf("../lib/lib.jar"), emptyList())
      pluginHeader("com.foo", legacyJpsModule("foo"),
                   includedJpsModule("foo"), includedEmbeddedModule(libId))
    }
  }

  @Test
  fun `two plugins include same project-level library`() {
    val foo = addModule("foo")
    val bar = addModule("bar")
    val lib = project.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    foo.dependenciesList.addLibraryDependency(lib)
    bar.dependenciesList.addLibraryDependency(lib)
    lib.addRoot(getUrl("project/lib"), JpsOrderRootType.COMPILED)
    val fooPlugin = createHeader("foo")
    val barPlugin = createHeader("bar")
    val pluginConfigurationModuleToDistributionEntries = mapOf(
      fooPlugin.pluginDescriptorJpsModuleName to listOf(
        moduleOutput("foo", pathPrefix = "plugins/foo/lib/"),
        projectLibraryEntry("lib", tempDirectory.rootPath.resolve("plugins/foo/lib/lib.jar"), "lib.jar"),
      ),
      barPlugin.pluginDescriptorJpsModuleName to listOf(
        moduleOutput("bar", pathPrefix = "plugins/bar/lib/"),
        projectLibraryEntry("lib", tempDirectory.rootPath.resolve("plugins/bar/lib/lib.jar"), "lib.jar"),
      ),
    )
    val libIdInFoo = RuntimeModuleId.raw("lib", "com.foo_${RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX}")
    val libIdInBar = RuntimeModuleId.raw("lib", "com.bar_${RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX}")
    generateAndCheck(listOf(fooPlugin, barPlugin), pluginConfigurationModuleToDistributionEntries) {
      descriptor(legacyJpsModule("foo"),listOf("../plugins/foo/lib/foo.jar"), listOf(libIdInFoo))
      descriptor(libIdInFoo,listOf("../plugins/foo/lib/lib.jar"), emptyList())
      pluginHeader("com.foo", legacyJpsModule("foo"), includedJpsModule("foo"), includedEmbeddedModule(libIdInFoo))

      descriptor(legacyJpsModule("bar"),listOf("../plugins/bar/lib/bar.jar"), listOf(libIdInBar))
      descriptor(libIdInBar,listOf("../plugins/bar/lib/lib.jar"), emptyList())
      pluginHeader("com.bar", legacyJpsModule("bar"), includedJpsModule("bar"), includedEmbeddedModule(libIdInBar))
    }
  }

  private fun generateAndCheck(
    plugin: PluginDescriptorDataForHeader,
    distributionEntries: List<DistributionFileEntry>,
    expected: ExpectedRuntimeRepositoryBuilder.() -> Unit,
  ) {
    generateAndCheck(listOf(plugin), mapOf(plugin.pluginDescriptorJpsModuleName to distributionEntries), expected)
  }

  private fun generateAndCheck(
    plugins: List<PluginDescriptorDataForHeader>,
    pluginConfigurationModuleToDistributionEntries: Map<String, List<DistributionFileEntry>>,
    expected: ExpectedRuntimeRepositoryBuilder.() -> Unit,
  ) {
    val pluginHeadersData = generateRuntimePluginHeaders(
      plugins,
      pluginConfigurationModuleToDistributionEntries,
      { it: Path -> tempDirectory.rootPath.relativize(it) },
      project
    )
    val descriptors = generateRuntimeModuleDescriptors(pluginHeadersData)
    val pluginHeaders = pluginHeadersData.map { it.header }
    val runtimeModuleRepositoryData = RawRuntimeModuleRepositoryData.create(descriptors.associateBy { it.moduleId }, pluginHeaders, tempDirectory.rootPath)
    ExpectedRuntimeRepositoryBuilder().apply(expected).checkRuntimeModuleRepository(runtimeModuleRepositoryData)
  }

  private fun createHeader(pluginDescriptorModuleName: String, vararg contentModules: String): PluginDescriptorDataForHeader {
    return PluginDescriptorDataForHeader(
      pluginId = "com.$pluginDescriptorModuleName",
      pluginDescriptorJpsModuleName = pluginDescriptorModuleName,
      additionalFrontendOnlyPlugin = false,
      contentModules = contentModules.associateWith {
        ContentModuleRegistrationDataForHeader(
          it,
          namespace = DEFAULT_NAMESPACE,
          RuntimeModuleLoadingRule.OPTIONAL,
          requiredIfAvailable = null,
          visibility = RuntimeModuleVisibility.PUBLIC
        )
      }
    )
  }

  private fun moduleOutput(moduleName: String, relativeOutput: String = "$moduleName.jar", pathPrefix: String = "lib/"): ModuleOutputEntry = ModuleOutputEntry(
    tempDirectory.rootPath.resolve("$pathPrefix$relativeOutput"),
    ModuleItem(moduleName, relativeOutputFile = relativeOutput, reason = null),
    size = 0,
    hash = 0,
    relativeOutputFile = relativeOutput
  )

  private fun moduleLibraryFileEntry(moduleName: String, libraryName: String, path: Path, relativeOutputFile: String?) : DistributionFileEntry {
    return ModuleLibraryFileEntry(
      path = path,
      moduleName = moduleName,
      libraryName = libraryName,
      relativeOutputFile = relativeOutputFile,
      libraryFile = path,
      canonicalLibraryPath = null,
      size = 0,
      hash = 0,
      owner = null,
    )
  }

  private fun projectLibraryEntry(libraryName: String, path: Path, relativeOutputFile: String?) : DistributionFileEntry {
    return ProjectLibraryEntry(
      path = path,
      data = ProjectLibraryData(libraryName, reason = null, owner = null),
      libraryFile = path,
      canonicalLibraryPath = null,
      size = 0,
      hash = 0,
      relativeOutputFile = relativeOutputFile,
    )
  }
  private fun includedContentModule(moduleName: String): IncludedRuntimeModule {
    return IncludedRuntimeModuleImpl(contentModule(moduleName), RuntimeModuleLoadingRule.OPTIONAL, null)
  }

  private fun includedJpsModule(moduleName: String): IncludedRuntimeModule {
    return IncludedRuntimeModuleImpl(legacyJpsModule(moduleName), RuntimeModuleLoadingRule.EMBEDDED, null)
  }

  private fun includedEmbeddedModule(moduleId: RuntimeModuleId): IncludedRuntimeModule {
    return IncludedRuntimeModuleImpl(moduleId, RuntimeModuleLoadingRule.EMBEDDED, null)
  }

  private fun addModule(name: String, vararg dependencies: JpsModule): JpsModule {
    val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
    module.addSourceRoot(getUrl("$name/src"), JavaSourceRootType.SOURCE)
    for (dependency in dependencies) {
      module.dependenciesList.addModuleDependency(dependency)
    }
    return module
  }

  private fun getUrl(relativePath: String): String {
    return JpsPathUtil.pathToUrl(tempDirectory.rootPath.resolve(relativePath).absolutePathString())
  }

  private fun contentModule(name: String): RuntimeModuleId = RuntimeModuleId.contentModule(name, DEFAULT_NAMESPACE)
}

