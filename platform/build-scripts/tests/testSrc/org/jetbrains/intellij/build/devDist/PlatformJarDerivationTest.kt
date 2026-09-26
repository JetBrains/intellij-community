// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
  fun `missing content ownership reports the dependency chain`() {
    assertThatThrownBy {
      validatePlatformContentOwnership(
        modules = listOf(ModuleItem("missing", "missing.jar", "<- dependency <- root")),
        retainedJars = emptyMap(),
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Missing content ownership: missing in missing.jar; <- dependency <- root")
  }

  @Test
  fun `retained jars require exact ordered members`() {
    val first = ModuleItem("first", "util.jar", "addModule")
    val second = ModuleItem("second", "util.jar", "addModule")
    val retained = mapOf("util.jar" to listOf("first", "second"))
    validatePlatformContentOwnership(listOf(first, second), retained)
    assertThatThrownBy {
      validatePlatformContentOwnership(listOf(second, first), retained)
    }.hasMessageContaining("Changed retained jar util.jar")
    assertThatThrownBy {
      validatePlatformContentOwnership(listOf(first, second, ModuleItem("extra", "util.jar", "<- root")), retained)
    }.hasMessageContaining("Missing content ownership: extra").hasMessageContaining("Changed retained jar util.jar")
  }

  @Test
  fun `removed and migrated modules invalidate exceptions`() {
    val retained = mapOf("util.jar" to listOf("first"))
    assertThatThrownBy {
      validatePlatformContentOwnership(emptyList(), retained)
    }.hasMessageContaining("Stale content ownership exception: first in util.jar")
    assertThatThrownBy {
      validatePlatformContentOwnership(listOf(ModuleItem("first", "util.jar", ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES)), retained)
    }.hasMessageContaining("Stale content ownership exception: first in util.jar")
  }

  @Test
  fun `optional retained jars are checked only when present`() {
    val optional = mapOf("ext/main.jar" to listOf("starter"))
    validatePlatformContentOwnership(emptyList(), emptyMap(), optionalRetainedJars = optional)
    validatePlatformContentOwnership(listOf(ModuleItem("starter", "ext/main.jar", "addModule")), emptyMap(), optionalRetainedJars = optional)
    assertThatThrownBy {
      validatePlatformContentOwnership(listOf(ModuleItem("other", "ext/main.jar", "<- root")), emptyMap(), optionalRetainedJars = optional)
    }.hasMessageContaining("Changed retained jar ext/main.jar")
  }

  @Test
  fun `content modules cannot have duplicate or explicit ownership`() {
    val content = ModuleItem("content", "content.jar", ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES)
    validatePlatformContentOwnership(listOf(content), emptyMap())
    assertThatThrownBy {
      validatePlatformContentOwnership(listOf(content, content), emptyMap())
    }.hasMessageContaining("Duplicate platform ownership for content")
    assertThatThrownBy {
      validatePlatformContentOwnership(listOf(content), emptyMap(), explicitModuleNames = listOf("content"))
    }.hasMessageContaining("Content module content also has explicit platform ownership")
  }

  @Test
  fun `content ownership preserves fixed bundle membership and order`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val layout = PlatformLayout()
    val members = listOf("intellij.platform.util.second", "intellij.platform.util.first", "intellij.platform.content")
    for (name in members) {
      project.addModule(name, JpsJavaModuleType.INSTANCE)
    }
    layout.withModules(sequenceOf(
      ModuleItem(members[0], "util.jar", "addModule"),
      ModuleItem(members[1], "util.jar", "addModule"),
      ModuleItem(members[2], "content.jar", ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES),
    ))

    val rows = derivePlatformJars(product = "demo", layout = layout, findModule = { requireNotNull(project.findModuleByName(it)) })

    assertThat(rows.contentModules.map { it.module }).containsExactly("intellij.platform.content")
    assertThat(rows.jars.map { it.relativeOutputFile }).containsExactly("util.jar", "content.jar")
    assertThat(rows.jars.first().members).containsExactly("intellij.platform.util.second", "intellij.platform.util.first")
    assertThat(rows.jars.last().members).containsExactly("intellij.platform.content")
  }

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
  fun `a fixed jar declares its layout-placed project libraries and merges only module libraries`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val first = project.addModule("intellij.platform.util.first", JpsJavaModuleType.INSTANCE)
    first.addProjectLibrary(project, "b-lib")
    val second = project.addModule("intellij.platform.util.second", JpsJavaModuleType.INSTANCE)
    second.dependenciesList.addLibraryDependency(second.libraryCollection.addLibrary("local", JpsJavaLibraryType.INSTANCE)).setScope(JpsJavaDependencyScope.COMPILE)

    val layout = PlatformLayout()
    layout.withModules(sequenceOf(
      ModuleItem("intellij.platform.util.first", "util.jar", "addModule"),
      ModuleItem("intellij.platform.util.second", "util.jar", "addModule"),
    ))
    layout.withProjectLibraries(sequenceOf("b-lib", "A-lib"), outPath = "util.jar")

    val rows = derivePlatformJars(product = "demo", layout = layout, findModule = { requireNotNull(project.findModuleByName(it)) })

    assertThat(rows.jars.single().relativeOutputFile).isEqualTo("util.jar")
    assertThat(rows.jars.single().members).containsExactly("intellij.platform.util.first", "intellij.platform.util.second")
    // The layout places both libraries in the jar itself, so they are declared rows with the jar path and not merged rows.
    assertThat(rows.libraries.map { Triple(it.library, it.relativeOutputFile, it.moduleName) })
      .containsExactly(Triple("b-lib", "util.jar", null), Triple("A-lib", "util.jar", null))
    assertThat(rows.mergedLibraries.map { it.library to it.relativeOutputFile }).containsExactly("local" to "util.jar")
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
