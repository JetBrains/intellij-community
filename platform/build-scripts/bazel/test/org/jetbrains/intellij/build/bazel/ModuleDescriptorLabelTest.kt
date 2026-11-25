// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaModuleType
import java.nio.file.Files
import java.nio.file.Path

/**
 * The descriptor label of a module, which the targets JSON records and the record of the module names.
 *
 * The module has four production resource roots in `.iml` order: one that the jar maps into `META-INF`, one outside the
 * package, and two plain ones. Each root holds `<module name>.xml`. A test resource root never gives the label.
 */
class ModuleDescriptorLabelTest {
  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  private val moduleName = "intellij.test.content"

  private fun checkout(): Path = tempFolder.root.toPath().toRealPath()

  private fun resources(checkout: Path): List<ResourceDescriptor> {
    val result = ArrayList<ResourceDescriptor>()
    for ((directory, relativeOutputPath) in listOf("content/remapped" to "META-INF", "outside" to "", "content/first" to "", "content/second" to "")) {
      val root = checkout.resolve("community/plugins/test/$directory")
      Files.createDirectories(root)
      Files.writeString(root.resolve("$moduleName.xml"), "<idea-plugin/>")
      result.add(ResourceDescriptor(
        baseDirectory = directory,
        files = emptyList(),
        relativeOutputPath = relativeOutputPath,
        root = root,
        excludes = emptyList(),
        allowEmpty = false,
      ))
    }
    return result
  }

  private fun communityPackage(checkout: Path): ModulePackage {
    return ModulePackage(
      directory = checkout.resolve("community/plugins/test/content"),
      labelPrefix = "@community//plugins/test/content",
      isCommunity = true,
      communityRoot = checkout.resolve("community"),
    )
  }

  @Test
  fun `the first plain resource root inside the package gives the label`() {
    val checkout = checkout()
    val resources = resources(checkout)
    val modulePackage = communityPackage(checkout)

    assertEquals("@community//plugins/test/content:first/$moduleName.xml", moduleDescriptorLabel(moduleName, resources, modulePackage))

    // The second root gives the label when the first one holds no descriptor.
    Files.delete(checkout.resolve("community/plugins/test/content/first/$moduleName.xml"))
    assertEquals("@community//plugins/test/content:second/$moduleName.xml", moduleDescriptorLabel(moduleName, resources, modulePackage))

    // The remapped root and the root outside the package give no label.
    Files.delete(checkout.resolve("community/plugins/test/content/second/$moduleName.xml"))
    assertNull(moduleDescriptorLabel(moduleName, resources, modulePackage))
  }

  @Test
  fun `a test resource root gives no label`() {
    val checkout = checkout()
    val packageDirectory = checkout.resolve("community/plugins/test/content")
    val testResources = resources(checkout).filter { it.relativeOutputPath.isEmpty() && it.root.startsWith(packageDirectory) }
    val imlFile = packageDirectory.resolve("$moduleName.iml")
    Files.writeString(imlFile, "<module/>")
    val module = ModuleDescriptor(
      imlFile = imlFile,
      module = JpsElementFactory.getInstance().createModel().project.addModule(moduleName, JpsJavaModuleType.INSTANCE),
      contentRoots = listOf(packageDirectory),
      sources = emptyList(),
      resources = emptyList(),
      testSources = emptyList(),
      testResources = testResources,
      isCommunity = true,
      bazelBuildFileDir = packageDirectory,
      relativePathFromProjectRoot = Path.of("plugins/test/content"),
      targetName = "content",
    )

    assertNull(moduleDescriptorLabel(module = module, communityRoot = checkout.resolve("community"), ultimateRoot = checkout))
  }

  @Test
  fun `the record names the descriptor of the label and no other file of that name`() {
    val checkout = checkout()
    val packageDirectory = checkout.resolve("community/plugins/test/content")
    val resources = resources(checkout)
    val imlFile = packageDirectory.resolve("$moduleName.iml")
    Files.writeString(imlFile, "<module/>")
    val module = ModuleDescriptor(
      imlFile = imlFile,
      module = JpsElementFactory.getInstance().createModel().project.addModule(moduleName, JpsJavaModuleType.INSTANCE),
      contentRoots = listOf(packageDirectory),
      sources = emptyList(),
      resources = resources,
      testSources = emptyList(),
      testResources = emptyList(),
      isCommunity = true,
      bazelBuildFileDir = packageDirectory,
      relativePathFromProjectRoot = Path.of("plugins/test/content"),
      targetName = "content",
    )

    // The remapped root and the second plain root also hold the file, and the record leaves them out.
    val label = moduleDescriptorLabel(module = module, communityRoot = checkout.resolve("community"), ultimateRoot = checkout)
    assertEquals("@community//plugins/test/content:first/$moduleName.xml", label)
    assertEquals(listOf(label), conventionalDescriptorLabels(module = module, communityRoot = checkout.resolve("community"), ultimateRoot = checkout))
  }

  @Test
  fun `a file on the other repository half gives no label`() {
    val checkout = checkout()
    // An ultimate module in the root package: every descriptor lies below the community checkout.
    val modulePackage = ModulePackage(directory = checkout, labelPrefix = "//", isCommunity = false, communityRoot = checkout.resolve("community"))

    assertNull(moduleDescriptorLabel(moduleName, resources(checkout), modulePackage))
  }
}
