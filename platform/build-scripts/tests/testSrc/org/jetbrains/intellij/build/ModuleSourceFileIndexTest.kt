// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The index answers as [findFileInModuleSources] does, and it answers a whole-project question once per path. */
class ModuleSourceFileIndexTest {
  @TempDir
  lateinit var tempDirectory: Path

  private val project: JpsProject = JpsElementFactory.getInstance().createModel().project

  @Test
  fun `the index finds a file where findFileInModuleSources finds it`() {
    val plugin = addModule("demo.plugin")
    val resources = addRoot(plugin, "resources") { plugin.addSourceRoot(it, JavaResourceRootType.RESOURCE) }
    writeFile(resources, "META-INF/plugin.xml")
    writeFile(resources, "demo.plugin.xml")
    val sources = addRoot(plugin, "src") {
      plugin.addSourceRoot(it, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties("com.demo"))
    }
    writeFile(sources, "nested/Source.kt")
    val testResources = addRoot(plugin, "testResources") { plugin.addSourceRoot(it, JavaResourceRootType.TEST_RESOURCE) }
    writeFile(testResources, "META-INF/test.xml")
    writeFile(testResources, "META-INF/plugin.xml")

    val index = ModuleSourceFileIndex(project.modules)
    for (path in listOf(
      "META-INF/plugin.xml", "/META-INF/plugin.xml", "demo.plugin.xml", "com/demo/nested/Source.kt", "nested/Source.kt",
      "META-INF/test.xml", "META-INF/missing.xml", "missing.xml", "META-INF", "missing/plugin.xml",
    )) {
      for (onlyProductionSources in listOf(true, false)) {
        assertThat(index.find(plugin, path, onlyProductionSources))
          .describedAs("path=%s onlyProductionSources=%s", path, onlyProductionSources)
          .isEqualTo(findFileInModuleSources(plugin, path, onlyProductionSources))
      }
    }
    assertThat(index.find(plugin, "META-INF/plugin.xml", onlyProductionSources = true)).isEqualTo(resources.resolve("META-INF/plugin.xml"))
    assertThat(index.find(plugin, "META-INF/test.xml", onlyProductionSources = true)).isNull()
    assertThat(index.find(plugin, "META-INF/test.xml", onlyProductionSources = false)).isEqualTo(testResources.resolve("META-INF/test.xml"))
    assertThat(index.find(plugin, "com/demo/nested/Source.kt", onlyProductionSources = true)).isEqualTo(sources.resolve("nested/Source.kt"))
  }

  @Test
  fun `the owners of a path are the modules with the file in the production sources, in the project order`() {
    val first = addModule("demo.first")
    writeFile(addRoot(first, "resources") { first.addSourceRoot(it, JavaResourceRootType.RESOURCE) }, "META-INF/shared.xml")
    val testOnly = addModule("demo.testOnly")
    writeFile(addRoot(testOnly, "testResources") { testOnly.addSourceRoot(it, JavaResourceRootType.TEST_RESOURCE) }, "META-INF/shared.xml")
    addModule("demo.empty")
    val last = addModule("demo.last")
    writeFile(addRoot(last, "resources") { last.addSourceRoot(it, JavaResourceRootType.RESOURCE) }, "META-INF/shared.xml")

    val index = ModuleSourceFileIndex(project.modules)

    assertThat(index.findOwners("META-INF/shared.xml")).containsExactly(first, last)
    assertThat(index.findOwners("META-INF/missing.xml")).isEmpty()
  }

  private fun addModule(name: String): JpsModule = project.addModule(name, JpsJavaModuleType.INSTANCE)

  /** Creates the directory of a source root of [module] and adds the root through [add], which gets the URL of the directory. */
  private fun addRoot(module: JpsModule, name: String, add: (url: String) -> Unit): Path {
    val root = Files.createDirectories(tempDirectory.resolve(module.name).resolve(name))
    add(root.toUri().toString())
    return root
  }

  private fun writeFile(root: Path, relativePath: String) {
    val file = root.resolve(relativePath)
    Files.createDirectories(file.parent)
    Files.writeString(file, "<idea-plugin/>")
  }
}
