// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

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

private fun newDependencyHelper(): Any {
  val helperClass = Class.forName("org.jetbrains.intellij.build.JarPackagerDependencyHelper")
  val constructor = helperClass.getDeclaredConstructor(ModuleOutputProvider::class.java)
  constructor.isAccessible = true
  return constructor.newInstance(EmptyModuleOutputProvider)
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
