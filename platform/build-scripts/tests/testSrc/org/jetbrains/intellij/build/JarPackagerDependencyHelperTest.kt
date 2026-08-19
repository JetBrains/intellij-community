// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class JarPackagerDependencyHelperTest {
  @Test
  fun `recognizes a selectively enabled test plugin module`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.sample._test", JpsJavaModuleType.INSTANCE)
    val helper = newDependencyHelper(SelectiveTestOutputProvider(module))

    assertThat(helper.isTestPluginModule(module.name, module)).isTrue()
    assertThat(helper.isTestPluginModule(module.name, null)).isTrue()
  }

  @Test
  fun `descriptor search reads only the test output of a test-only module`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.sample.tests", JpsJavaModuleType.INSTANCE)
    val provider = RecordingOutputProvider(module = module, testCompilationOutputEnabled = true, testOutput = DESCRIPTOR)

    val content = runBlocking {
      readDescriptor(module = module, path = "intellij.sample.tests.xml", outputProvider = provider, pass = DescriptorSearchPass.MODULE_OUTPUT)
    }

    assertThat(content).isEqualTo(DESCRIPTOR)
    // The negative half is the point: a production read is what declares the empty stub jar of a `.tests` module as
    // an input of a dev-distribution fragment, and the fragment never declared it.
    assertThat(provider.requestedForTests).containsExactly(true)
  }

  @Test
  fun `descriptor search reads the production output of an ordinary module`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.sample", JpsJavaModuleType.INSTANCE)
    val provider = RecordingOutputProvider(module = module, testCompilationOutputEnabled = false, productionOutput = DESCRIPTOR)

    val content = runBlocking {
      readDescriptor(module = module, path = "intellij.sample.xml", outputProvider = provider, pass = DescriptorSearchPass.MODULE_OUTPUT)
    }

    assertThat(content).isEqualTo(DESCRIPTOR)
    assertThat(provider.requestedForTests).containsExactly(false)
  }

  @Test
  fun `caches library dependencies separately for test runtime`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.test.module", JpsJavaModuleType.INSTANCE)
    module.dependenciesList.addLibraryDependency(project.libraryCollection.addLibrary("production-lib", JpsJavaLibraryType.INSTANCE))
      .setScope(JpsJavaDependencyScope.COMPILE)
    module.dependenciesList.addLibraryDependency(project.libraryCollection.addLibrary("test-lib", JpsJavaLibraryType.INSTANCE))
      .setScope(JpsJavaDependencyScope.TEST)

    val helper = newDependencyHelper()
    assertThat(helper.getLibraryDependencies(module, withTests = false).libraryNames()).containsExactly("production-lib")
    assertThat(helper.getLibraryDependencies(module, withTests = true).libraryNames()).containsExactly("production-lib", "test-lib")
    assertThat(helper.getLibraryDependencies(module, withTests = false).libraryNames()).containsExactly("production-lib")

    val reverseOrderHelper = newDependencyHelper()
    assertThat(reverseOrderHelper.getLibraryDependencies(module, withTests = true).libraryNames()).containsExactly("production-lib", "test-lib")
    assertThat(reverseOrderHelper.getLibraryDependencies(module, withTests = false).libraryNames()).containsExactly("production-lib")
  }
}

private val DESCRIPTOR = "<idea-plugin/>".toByteArray()

private fun newDependencyHelper(outputProvider: ModuleOutputProvider = EmptyModuleOutputProvider): Any {
  val helperClass = Class.forName("org.jetbrains.intellij.build.JarPackagerDependencyHelper")
  val constructor = helperClass.getDeclaredConstructor(ModuleOutputProvider::class.java)
  constructor.isAccessible = true
  return constructor.newInstance(outputProvider)
}

private fun Any.isTestPluginModule(moduleName: String, module: JpsModule?): Boolean {
  val method = javaClass.getDeclaredMethod("isTestPluginModule", String::class.java, JpsModule::class.java)
  method.isAccessible = true
  return method.invoke(this, moduleName, module) as Boolean
}

private fun Any.getLibraryDependencies(module: JpsModule, withTests: Boolean): List<JpsLibraryDependency> {
  val method = javaClass.getDeclaredMethod("getLibraryDependencies", JpsModule::class.java, java.lang.Boolean.TYPE)
  method.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  return method.invoke(this, module, withTests) as List<JpsLibraryDependency>
}

private fun JpsLibraryDependency.setScope(scope: JpsJavaDependencyScope) {
  JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(this).scope = scope
}

private fun List<JpsLibraryDependency>.libraryNames(): List<String> {
  return map { it.libraryReference.libraryName }
}

private object EmptyModuleOutputProvider : ModuleOutputProvider {
  override val useTestCompilationOutput: Boolean = false

  override fun findModule(name: String): JpsModule? = null

  override fun getModuleImlFile(module: JpsModule): Path = error("Not needed")

  override fun findRequiredModule(name: String): JpsModule = error("Not needed")

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> = emptyList()

  override fun getProjectLibraryToModuleMap(): Map<String, String> = emptyMap()

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = emptyList()

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? = null
}

private class SelectiveTestOutputProvider(private val selectedModule: JpsModule) : ModuleOutputProvider by EmptyModuleOutputProvider {
  override fun findModule(name: String): JpsModule? = selectedModule.takeIf { module -> module.name == name }

  override fun isTestCompilationOutputEnabled(module: JpsModule): Boolean = module == selectedModule
}

/** Records which side of a module's output a read asked for, so a test can assert what was *not* asked for. */
private class RecordingOutputProvider(
  private val module: JpsModule,
  private val testCompilationOutputEnabled: Boolean,
  private val productionOutput: ByteArray? = null,
  private val testOutput: ByteArray? = null,
) : ModuleOutputProvider by EmptyModuleOutputProvider {
  @JvmField val requestedForTests: MutableList<Boolean> = ArrayList()

  override fun findModule(name: String): JpsModule? = module.takeIf { it.name == name }

  override fun isTestCompilationOutputEnabled(module: JpsModule): Boolean = testCompilationOutputEnabled

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    requestedForTests.add(forTests)
    return if (forTests) testOutput else productionOutput
  }
}
