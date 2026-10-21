// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.files

import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.workspaceModel.update
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestApplication
class JavaSourceFilesFunctionsTest {
  @RegisterExtension
  @JvmField
  val projectModel = ProjectModelExtension()
  
  private val moduleEntity: ModuleEntity
    get() = projectModel.project.workspaceModel.currentSnapshot.resolve(ModuleId(module.name))!!

  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
  }

  @Test
  fun `find file by path from source root`() = runBlocking {
    val (aFile, bFile) = edtWriteAction {
      val srcRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
      val aFile = srcRoot.createFile("a.txt")
      val bFile = srcRoot.createFile("b/c.txt")
      aFile to bFile
    }
    assertEquals(aFile, moduleEntity.findResourceFileByRelativePath("a.txt"))
    assertEquals(bFile, moduleEntity.findResourceFileByRelativePath("b/c.txt"))
    assertNull(moduleEntity.findResourceFileByRelativePath("c.txt"))
  }
  
  @Test
  fun `find file by path from source root with package prefix`() = runBlocking {
    val aFile = edtWriteAction {
      val srcRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
      srcRoot.createFile("a.txt")
    }
    projectModel.project.workspaceModel.update { storage ->
      val entity = storage.entities(JavaSourceRootPropertiesEntity::class.java).first()
      storage.modifyEntity(JavaSourceRootPropertiesEntity.Builder::class.java, entity) {
        packagePrefix = "foo"
      }
    }
    assertNull(moduleEntity.findResourceFileByRelativePath("a.txt"))
    assertEquals(aFile, moduleEntity.findResourceFileByRelativePath("foo/a.txt"))
  }
  
  @Test
  fun `find file by path from resource root with prefix`() = runBlocking {
    val aFile = edtWriteAction {
      val srcRoot = projectModel.addSourceRoot(module, "src", JavaResourceRootType.RESOURCE)
      srcRoot.createFile("a.txt")
    }
    projectModel.project.workspaceModel.update { storage ->
      val entity = storage.entities(JavaResourceRootPropertiesEntity::class.java).first()
      storage.modifyEntity(JavaResourceRootPropertiesEntity.Builder::class.java, entity) {
        relativeOutputPath = "foo"
      }
    }
    assertNull(moduleEntity.findResourceFileByRelativePath("a.txt"))
    assertEquals(aFile, moduleEntity.findResourceFileByRelativePath("foo/a.txt"))
  }

  @Test
  fun `find files in directory root`() {
    val pluginXml = addFile("resources/META-INF/plugin.xml")
    val moduleXml = addFile("resources/example.xml")
    addResourceRoot(pluginXml.parent.parent)

    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("META-INF/plugin.xml"))
    assertEquals(moduleXml, moduleEntity.findResourceFileByRelativePath("/example.xml"))
    assertNull(moduleEntity.findResourceFileByRelativePath("META-INF"))
    assertEquals(listOf(moduleXml), moduleEntity.findResourceFilesInDirectory("").toList())
  }

  @Test
  fun `find files in directory root with relative output path`() {
    val pluginXml = addFile("resources/meta/plugin.xml")
    addResourceRoot(pluginXml.parent, relativeOutputPath = "foo/META-INF")

    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("foo/META-INF/plugin.xml"))
    assertNull(moduleEntity.findResourceFileByRelativePath("plugin.xml"))
    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("/foo/META-INF/plugin.xml"))
    assertEquals(listOf(pluginXml), moduleEntity.findResourceFilesInDirectory("foo/META-INF").toList())
    assertEquals(listOf(pluginXml), moduleEntity.findResourceFilesInDirectory("/foo/META-INF/").toList())
    assertEquals(emptyList(), moduleEntity.findResourceFilesInDirectory("").toList())
  }

  @Test
  fun `find files with dots in output path`() {
    val productModulesXml = addFile("resources/product-modules.xml")
    addResourceRoot(productModulesXml.parent, relativeOutputPath = "META-INF/intellij.example")

    assertEquals(productModulesXml, moduleEntity.findResourceFileByRelativePath("META-INF/intellij.example/product-modules.xml"))
    assertNull(moduleEntity.findResourceFileByRelativePath("META-INF/intellij/example/product-modules.xml"))
    assertEquals(listOf(productModulesXml), moduleEntity.findResourceFilesInDirectory("META-INF/intellij.example").toList())
  }

  @Test
  fun `find files in single-file roots`() {
    val pluginXml = addFile("resources/META-INF/plugin.xml")
    val bundle = addFile("resources/messages/ExampleBundle.properties")
    addResourceRoot(pluginXml, relativeOutputPath = "META-INF")
    addResourceRoot(bundle)

    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("META-INF/plugin.xml"))
    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("/META-INF/plugin.xml/"))
    assertEquals(bundle, moduleEntity.findResourceFileByRelativePath("ExampleBundle.properties"))
    assertEquals(bundle, moduleEntity.findResourceFileByRelativePath("/ExampleBundle.properties"))
    assertNull(moduleEntity.findResourceFileByRelativePath("messages/ExampleBundle.properties"))
    assertEquals(listOf(bundle), moduleEntity.findResourceFilesInDirectory("").toList())
    assertEquals(listOf(pluginXml), moduleEntity.findResourceFilesInDirectory("META-INF").toList())
    assertEquals(listOf(pluginXml), moduleEntity.findResourceFilesInDirectory("/META-INF/").toList())
  }

  @Test
  fun `find files in source root with package prefix`() {
    val bundle = addFile("src/messages/ExampleBundle.properties")
    addRoot(bundle.parent.parent, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties("com.example"))

    assertEquals(bundle, moduleEntity.findResourceFileByRelativePath("com/example/messages/ExampleBundle.properties"))
    assertNull(moduleEntity.findResourceFileByRelativePath("messages/ExampleBundle.properties"))
  }

  @Test
  fun `find files in source root whose directories are not packages`() {
    val pluginXml = addFile("src/META-INF/plugin.xml")
    val properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("")
    properties.isPackageMatchesDirectory = false
    addRoot(pluginXml.parent.parent, JavaSourceRootType.SOURCE, properties)

    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("META-INF/plugin.xml"))
  }

  @Test
  fun `find files in roots of given types`() {
    val resourceXml = addFile("resources/example.xml")
    val sourceXml = addFile("src/other.xml")
    addResourceRoot(resourceXml.parent)
    addRoot(sourceXml.parent, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties(""))
    val resourceTypes = setOf(JavaResourceRootType.RESOURCE)

    assertEquals(sourceXml, moduleEntity.findResourceFileByRelativePath("other.xml"))
    assertNull(moduleEntity.findResourceFileByRelativePath("other.xml", resourceTypes))
    assertEquals(listOf(resourceXml), moduleEntity.findResourceFilesInDirectory("", resourceTypes).toList())
    assertEquals(setOf(resourceXml, sourceXml), moduleEntity.findResourceFilesInDirectory("").toSet())
  }

  @Test
  fun `find files in test roots only with root types`() {
    val pluginXml = addFile("testResources/META-INF/plugin.xml")
    addRoot(pluginXml.parent.parent, JavaResourceRootType.TEST_RESOURCE, JavaResourceRootProperties("", false))

    assertNull(moduleEntity.findResourceFileByRelativePath("META-INF/plugin.xml"))
    assertEquals(pluginXml, moduleEntity.findResourceFileByRelativePath("META-INF/plugin.xml", JavaModuleSourceRootTypes.RESOURCES))
  }

  @Test
  fun `skip directory with requested name`() {
    val directory = projectModel.baseProjectDir.newVirtualDirectory("${module.name}/first/example.xml")
    val moduleXml = addFile("second/example.xml")
    addResourceRoot(directory.parent)
    addResourceRoot(moduleXml.parent)

    assertEquals(moduleXml, moduleEntity.findResourceFileByRelativePath("example.xml"))
  }

  @Test
  fun `match file name with case sensitivity of file system`() {
    val pluginXml = addFile("resources/META-INF/plugin.xml")
    addResourceRoot(pluginXml.parent.parent)

    val expected = if (pluginXml.parent.isCaseSensitive) null else pluginXml
    assertEquals(expected, moduleEntity.findResourceFileByRelativePath("META-INF/Plugin.xml"))
  }

  private fun addFile(path: String): VirtualFile = projectModel.baseProjectDir.newVirtualFile("${module.name}/$path")

  private fun addResourceRoot(root: VirtualFile, relativeOutputPath: String = "") {
    addRoot(root, JavaResourceRootType.RESOURCE, JavaResourceRootProperties(relativeOutputPath, false))
  }

  private fun <P : JpsElement> addRoot(root: VirtualFile, rootType: JpsModuleSourceRootType<P>, properties: P) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      val moduleDir = projectModel.baseProjectDir.virtualFileRoot.findChild(module.name)!!
      val contentEntry = model.contentEntries.firstOrNull() ?: model.addContentEntry(moduleDir)
      contentEntry.addSourceFolder(root, rootType, properties)
    }
  }
}