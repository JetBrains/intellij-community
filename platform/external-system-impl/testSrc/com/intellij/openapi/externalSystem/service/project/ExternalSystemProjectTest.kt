// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.*
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.test.ExternalSystemProjectTestCase
import com.intellij.openapi.externalSystem.test.ExternalSystemTestCase.collectRootsInside
import com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil.assertMapsEqual
import com.intellij.openapi.externalSystem.test.Project
import com.intellij.openapi.externalSystem.test.toDataNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.PathUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class ExternalSystemProjectTest : ExternalSystemProjectTestCase() {

  @Test
  fun `test module names deduplication`() {
    val projectModel = project {
      module("root", externalProjectPath = "root")
      module("root", externalProjectPath = "root/1")
      module("root", externalProjectPath = "root/2")
      module("root", externalProjectPath = "root/3")
      module("root", externalProjectPath = "another/root")
      module("root", externalProjectPath = "another/notRoot")
      module("root", externalProjectPath = "root/root/root")
      module("root", externalProjectPath = "root/root/root/root")
      module("root", externalProjectPath = "yetanother/root/root")
      module("group-root", externalProjectPath = "root")
      module("group-root", externalProjectPath = "root/group/root")
      module("group-root", externalProjectPath = "root/my/group/root")
      module("group-root", externalProjectPath = "root/my-group/root")
    }

    val modelsProvider = IdeModelsProviderImpl(project)
    applyProjectModel(projectModel)

    val expectedNames = arrayOf(
      "root", "1.root", "2.root", "3.root", "another.root", "notRoot.root", "root.root", "root.root.root", "yetanother.root.root",
      "group-root", "root.group-root", "group.root.group-root", "my-group.root.group-root"
    )
    assertOrderedEquals(modelsProvider.modules.map { it.name }, *expectedNames)

    // check reimport with the same data
    applyProjectModel(projectModel)
    assertOrderedEquals(modelsProvider.modules.map { it.name }, *expectedNames)
  }

  @Test
  fun `test no duplicate library dependency is added on subsequent refresh when there is an unresolved library`() {
    val projectModel = project {
      module {
        lib("lib1")
        lib("lib2", unresolved = true)
      }
    }

    applyProjectModel(projectModel, projectModel)

    val modelsProvider = IdeModelsProviderImpl(project)
    val module = modelsProvider.findIdeModule("module")
    val entries = modelsProvider.getOrderEntries(module!!)
    val dependencies = mutableMapOf<String?, Int>()
    entries.groupingBy { (it as? LibraryOrderEntry)?.libraryName }.eachCountTo(dependencies).remove(null)
    assertMapsEqual(mapOf("Test_external_system_id: lib1" to 1, "Test_external_system_id: lib2" to 1), dependencies)
  }

  @Test
  fun `test no duplicate module dependency is added on subsequent refresh when duplicated dependencies exist`() {
    val projectModel = project {
      module("module1")
      module("module2") {
        moduleDependency("module1", DependencyScope.RUNTIME)
        moduleDependency("module1", DependencyScope.TEST)
      }
    }

    applyProjectModel(projectModel)

    val assertOrderEntries = {
      val modelsProvider = IdeModelsProviderImpl(project)
      val module = modelsProvider.findIdeModule("module2")
      val dependencies = modelsProvider.getOrderEntries(module!!)
        .filterIsInstance<ExportableOrderEntry>().map { it.presentableName to it.scope }
      val expected = listOf("module1" to DependencyScope.RUNTIME, "module1" to DependencyScope.TEST)
      assertThat(dependencies).containsExactlyInAnyOrderElementsOf(expected)
    }
    assertOrderEntries.invoke()

    // change dependency scope to test to get duplicated order entries
    with(IdeModifiableModelsProviderImpl(project)) {
      val modifiableRootModel = getModifiableRootModel(findIdeModule("module2"))
      modifiableRootModel.orderEntries.filterIsInstance<ExportableOrderEntry>().forEach { it.scope = DependencyScope.TEST }
      runWriteAction { commit() }
    }

    applyProjectModel(projectModel)
    assertOrderEntries.invoke()
  }

  @Test
  fun `test optimized method for getting modules libraries order entries`() {
    val libBinPath = File(projectPath, "bin_path")
    val libSrcPath = File(projectPath, "source_path")

    FileUtil.createDirectory(libBinPath)
    FileUtil.createDirectory(libSrcPath)

    val projectModel = project {
      module {
        lib("", level = LibraryLevel.MODULE) {
          roots(LibraryPathType.BINARY, libBinPath.absolutePath)
        }
        lib("", level = LibraryLevel.MODULE) {
          roots(LibraryPathType.BINARY, libSrcPath.absolutePath)
        }
      }
    }

    applyProjectModel(projectModel, projectModel)

    val modelsProvider = IdeModelsProviderImpl(project)
    val projectNode = projectModel.toDataNode()
    val moduleNodeList = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)
    assertThat(moduleNodeList).hasSize(1)

    val moduleNode = moduleNodeList.first()
    assertEquals("module", moduleNode.data.moduleName)

    val libraryDependencyDataList = ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.LIBRARY_DEPENDENCY)
      .filter { it.data.level == LibraryLevel.MODULE }
      .map { it.data }

    val libraryOrderEntries = modelsProvider.findIdeModuleLibraryOrderEntries(moduleNode.data, libraryDependencyDataList)
    assertThat(libraryOrderEntries).hasSize(2)

    for ((libraryEntry, libraryData) in libraryOrderEntries) {
      val expected = libraryData.target.getPaths(LibraryPathType.BINARY).map(PathUtil::getLocalPath)
      val actual = libraryEntry.getUrls(OrderRootType.CLASSES).map { PathUtil.getLocalPath(VfsUtilCore.urlToPath(it)) }
      assertThat(expected).containsExactlyInAnyOrderElementsOf(actual)
    }
  }

  @Test
  fun `test changes in a project layout (content roots) could be detected on Refresh`() {
    val contentRoots = mutableMapOf(
      TEST to mutableListOf("src/test/resources", "/src/test/java", "src/test/groovy"),
      SOURCE to mutableListOf("src/main/resources", "src/main/java", "src/main/groovy"),
      EXCLUDED to mutableListOf(".gradle", "build")
    )

    (contentRoots[TEST]!! union contentRoots[SOURCE]!!).forEach {
      FileUtil.createDirectory(File(projectPath, it))
    }

    val projectModelBuilder: () -> Project = {
      project {
        module {
          contentRoot {
            for ((key, values) in contentRoots) {
              values.forEach {
                folder(type = key, relativePath = it)
              }
            }
          }
        }
      }
    }

    val projectModelInitial = projectModelBuilder.invoke()
    contentRoots[SOURCE]!!.removeFirst()
    contentRoots[TEST]!!.removeFirst()
    val projectModelRefreshed = projectModelBuilder.invoke()

    applyProjectModel(projectModelInitial, projectModelRefreshed)

    val modelsProvider = IdeModelsProviderImpl(project)
    val module = modelsProvider.findIdeModule("module")!!
    val entries = modelsProvider.getOrderEntries(module)

    val folders = mutableMapOf<String?, Int>()
    for (entry in entries) {
      if (entry is ModuleSourceOrderEntry) {
        val contentEntry = entry.rootModel.contentEntries.first()
        folders.merge("source", contentEntry.sourceFolders.size, Integer::sum)
        folders.merge("excluded", contentEntry.excludeFolders.size, Integer::sum)
      }
    }
    assertMapsEqual(mapOf("source" to 4, "excluded" to 2), folders)
  }

  @Test
  fun `test excluded directories merge`() {
    val contentRoots = mutableMapOf(
      EXCLUDED to mutableListOf(".gradle", "build")
    )

    val projectModelBuilder: () -> Project = {
      project {
        module {
          contentRoot {
            for ((key, values) in contentRoots) {
              values.forEach {
                folder(type = key, relativePath = it)
              }
            }
          }
        }
      }
    }

    val projectModelInitial = projectModelBuilder.invoke()
    contentRoots[EXCLUDED]!!.removeFirst()
    contentRoots[EXCLUDED]!!.add("newExclDir")
    val projectModelRefreshed = projectModelBuilder.invoke()
    applyProjectModel(projectModelInitial, projectModelRefreshed)

    val modelsProvider = IdeModelsProviderImpl(project)
    val module = modelsProvider.findIdeModule("module")!!
    val folders = mutableListOf<String>()
    modelsProvider.getOrderEntries(module)
      .filterIsInstance<ModuleSourceOrderEntry>()
      .flatMap { it.rootModel.contentEntries.asIterable() }
      .forEach { contentEntry -> folders.addAll(contentEntry.excludeFolders.map { File(it.url).name }) }

    assertThat(folders).containsExactlyInAnyOrderElementsOf(listOf(".gradle", "build", "newExclDir"))
  }

  @Test
  fun `test library dependency with sources path added on subsequent refresh`() {
    val libBinPath = File(projectPath, "bin_path")
    val libSrcPath = File(projectPath, "source_path")
    val libDocPath = File(projectPath, "doc_path")

    FileUtil.createDirectory(libBinPath)
    FileUtil.createDirectory(libSrcPath)
    FileUtil.createDirectory(libDocPath)

    applyProjectModel(
      project {
        module {
          lib("lib1", level = LibraryLevel.MODULE) {
            roots(LibraryPathType.BINARY, libBinPath.absolutePath)
          }
        }
      },
      project {
        module {
          lib("lib1", level = LibraryLevel.MODULE) {
            roots(LibraryPathType.BINARY, libBinPath.absolutePath)
            roots(LibraryPathType.SOURCE, libSrcPath.absolutePath)
          }
        }
      },
      project {
        module {
          lib("lib1", level = LibraryLevel.MODULE) {
            roots(LibraryPathType.BINARY, libBinPath.absolutePath)
            roots(LibraryPathType.SOURCE, libSrcPath.absolutePath)
            roots(LibraryPathType.DOC, libDocPath.absolutePath)
          }
        }
      }
    )

    val modelsProvider = IdeModelsProviderImpl(project)
    val module = modelsProvider.findIdeModule("module")!!
    val dependencies = mutableMapOf<String?, Int>()
    for (entry in modelsProvider.getOrderEntries(module)) {
      if (entry is LibraryOrderEntry) {
        val name = entry.libraryName
        dependencies.merge(name, 1, Integer::sum)
        if ("Test_external_system_id: lib1" == name) {
          val classesUrls = entry.getUrls(OrderRootType.CLASSES)
          assertThat(classesUrls).hasSize(1)
          assertTrue(classesUrls.first().endsWith("bin_path"))
          val sourceUrls = entry.getUrls(OrderRootType.SOURCES)
          assertThat(sourceUrls).hasSize(1)
          assertTrue(sourceUrls.first().endsWith("source_path"))
          val docUrls = entry.getUrls(JavadocOrderRootType.getInstance())
          assertThat(docUrls).hasSize(1)
          assertTrue(docUrls.first().endsWith("doc_path"))
        }
        else {
          fail()
        }
      }
    }
    assertMapsEqual(mapOf("Test_external_system_id: lib1" to 1), dependencies)
  }

  @Test
  fun `test package prefix setup`() {
    createProjectSubDirectory("src/main/java")
    applyProjectModel(
      project {
        module {
          contentRoot {
            folder(type = SOURCE, relativePath = "src/main/java", packagePrefix = "org.example")
          }
        }
      }
    )
    assertSourcePackagePrefix("module", "src/main/java", "org.example")
    applyProjectModel(
      project {
        module {
          contentRoot {
            folder(type = SOURCE, relativePath = "src/main/java", packagePrefix = "org.jetbrains")
          }
        }
      }
    )

    assertSourcePackagePrefix("module", "src/main/java", "org.jetbrains")
    applyProjectModel(
      project {
        module {
          contentRoot {
            folder(type = SOURCE, relativePath = "src/main/java", packagePrefix = "")
          }
        }
      }
    )
    assertSourcePackagePrefix("module", "src/main/java", "org.jetbrains")
  }

  @Test
  fun `test project SDK configuration import`() {
    val myJdkName = "My JDK"
    val myJdkHome = IdeaTestUtil.requireRealJdkHome()

    val allowedRoots = mutableListOf<String>()
    allowedRoots.add(myJdkHome)
    allowedRoots.addAll(collectRootsInside(myJdkHome))
    VfsRootAccess.allowRootAccess(testRootDisposable, *allowedRoots.toTypedArray())

    runWriteAction {
      val oldJdk = ProjectJdkTable.getInstance().findJdk(myJdkName)
      if (oldJdk != null) {
        ProjectJdkTable.getInstance().removeJdk(oldJdk)
      }
      val jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myJdkHome))!!
      val jdk = SdkConfigurationUtil.setupSdk(emptyArray(), jdkHomeDir, JavaSdk.getInstance(), true, null, myJdkName)
      assertNotNull("Cannot create JDK for $myJdkHome", jdk)
      ProjectJdkTable.getInstance().addJdk(jdk!!, project)
    }

    applyProjectModel(
      project {
        javaProject(compileOutputPath = "$projectPath/out",
                    languageLevel = LanguageLevel.JDK_1_7,
                    targetBytecodeVersion = "1.5")
      }
    )

    val languageLevelExtension = LanguageLevelProjectExtension.getInstance(project)
    assertEquals(LanguageLevel.JDK_1_7, languageLevelExtension.languageLevel)
    val compilerConfiguration = CompilerConfiguration.getInstance(project)
    assertEquals("1.5", compilerConfiguration.projectBytecodeTarget)
  }

  private fun assertSourcePackagePrefix(moduleName: String, sourcePath: String, packagePrefix: String) {
    val module = runReadAction { ModuleManager.getInstance(project).findModuleByName(moduleName) }
    assertNotNull("Module $moduleName not found", module)
    assertSourcePackagePrefix(module!!, sourcePath, packagePrefix)
  }

  private fun assertSourcePackagePrefix(module: com.intellij.openapi.module.Module, sourcePath: String, packagePrefix: String) {
    val rootManger = ModuleRootManager.getInstance(module)
    val sourceFolder = findSourceFolder(rootManger, sourcePath)
    assertNotNull("Source folder $sourcePath not found in module ${module.name}", sourceFolder)
    assertEquals(packagePrefix, sourceFolder!!.packagePrefix)
  }

  private fun findSourceFolder(moduleRootManager: ModuleRootModel, sourcePath: String): SourceFolder? {
    val contentEntries = moduleRootManager.contentEntries
    val module = moduleRootManager.module
    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    for (contentEntry in contentEntries) {
      for (sourceFolder in contentEntry.sourceFolders) {
        val folderPath = urlToPath(sourceFolder.url)
        val rootPath = urlToPath("$externalProjectPath/$sourcePath")
        if (folderPath == rootPath) return sourceFolder
      }
    }
    return null
  }

  private fun urlToPath(url: String): String {
    val path = VfsUtilCore.urlToPath(url)
    return FileUtil.toSystemIndependentName(path)
  }
}