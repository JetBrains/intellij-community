// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test

/**
 * The library half of the platform rows over an in-memory JPS model: which library the packer merges into a jar.
 *
 * The rows themselves are checked by the packaging gate of every product, which compares them against the packed
 * distribution.
 */
class PlatformJarDerivationTest {
  @Test
  fun `same-named declared libraries retain their project and module scopes`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val layout = PlatformLayout()
    layout.withProjectLibraries(sequenceOf("shared"), outPath = "shared.jar")
    layout.withModuleLibrary(libraryName = "shared", moduleName = "demo.first", relativeOutputPath = "shared.jar")
    layout.withModuleLibrary(libraryName = "shared", moduleName = "demo.second", relativeOutputPath = "shared.jar")

    val rows = derivePlatformJars(product = "demo", layout = layout, findModule = { requireNotNull(project.findModuleByName(it)) })

    assertThat(rows.libraries.map { it.library to it.moduleName })
      .containsExactly("shared" to null, "shared" to "demo.first", "shared" to "demo.second")
  }

  @Test
  fun `a product module jar merges its module libraries and its own project libraries`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val group = project.addModule("intellij.demo", JpsJavaModuleType.INSTANCE)
    group.addProjectLibrary(project, "shared")
    val core = project.addModule("intellij.demo.core", JpsJavaModuleType.INSTANCE)
    core.dependenciesList.addModuleDependency(group).setScope(JpsJavaDependencyScope.COMPILE)
    core.addProjectLibrary(project, "shared")
    core.addProjectLibrary(project, "own")
    core.addProjectLibrary(project, "layout-owned")
    core.addProjectLibrary(project, "test-only", JpsJavaDependencyScope.TEST)
    core.dependenciesList.addLibraryDependency(core.libraryCollection.addLibrary("local", JpsJavaLibraryType.INSTANCE)).setScope(JpsJavaDependencyScope.RUNTIME)
    core.dependenciesList.addLibraryDependency(core.libraryCollection.addLibrary("declared", JpsJavaLibraryType.INSTANCE)).setScope(JpsJavaDependencyScope.COMPILE)

    val layout = PlatformLayout()
    val groupItem = ModuleItem(moduleName = "intellij.demo", relativeOutputFile = "app.jar", reason = "platform")
    val coreItem = ModuleItem(moduleName = "intellij.demo.core", relativeOutputFile = "intellij.demo.core.jar", reason = ModuleIncludeReasons.PRODUCT_MODULES)
    layout.withModules(sequenceOf(groupItem, coreItem))
    layout.withProjectLibrary("layout-owned", "util.jar")
    layout.withModuleLibrary(libraryName = "declared", moduleName = "intellij.demo.core", relativeOutputPath = "declared.jar")

    // `shared` stays out because the group module brings it, `layout-owned` and `declared` because the layout declares
    // them, and `test-only` because it does not reach the production runtime.
    assertThat(mergedLibraryNames(item = coreItem, module = core, layout = layout)).containsExactly("own", "local")
  }

  @Test
  fun `a plain platform module jar merges no project library`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("intellij.platform.demo", JpsJavaModuleType.INSTANCE)
    module.addProjectLibrary(project, "own")
    module.dependenciesList.addLibraryDependency(module.libraryCollection.addLibrary("local", JpsJavaLibraryType.INSTANCE)).setScope(JpsJavaDependencyScope.COMPILE)

    val layout = PlatformLayout()
    layout.withModule("intellij.platform.demo")

    assertThat(mergedLibraryNames(item = layout.includedModules.single(), module = module, layout = layout)).containsExactly("local")
  }
}

private fun JpsModule.addProjectLibrary(project: JpsProject, name: String, scope: JpsJavaDependencyScope = JpsJavaDependencyScope.COMPILE) {
  val library = project.libraryCollection.findLibrary(name) ?: project.libraryCollection.addLibrary(name, JpsJavaLibraryType.INSTANCE)
  dependenciesList.addLibraryDependency(library).setScope(scope)
}

private fun JpsDependencyElement.setScope(scope: JpsJavaDependencyScope) {
  JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(this).scope = scope
}
