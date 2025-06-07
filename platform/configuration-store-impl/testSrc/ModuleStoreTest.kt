// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.Ksuid
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.SoftAssertions
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

const val ESCAPED_MODULE_DIR = "\$MODULE_DIR$"

@RunsInActiveStoreMode
class ModuleStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()

  @JvmField
  @Rule
  val ruleChain = RuleChain(tempDirManager, ActiveStoreRule(projectRule), DisposeModulesRule(projectRule))

  @Test
  @Suppress("DEPRECATION")
  fun `set option`() = runBlocking {
    val moduleFile = tempDirManager.createVirtualFile("test.iml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <module type="JAVA_MODULE" foo="bar" version="4">
            <component name="NewModuleRootManager" />
        </module>
        """.trimIndent())

    projectRule.loadModule(moduleFile).useAndDispose {
      assertThat(getOptionValue("foo")).isEqualTo("bar")

      setOption("foo", "not bar")
      project.stateStore.save()
    }

    projectRule.loadModule(moduleFile).useAndDispose {
      assertThat(getOptionValue("foo")).isEqualTo("not bar")

      setOption("foo", "not bar")
      // ensure that save the same data will not lead to any problems (like "Content equals, but it must be handled not at this level")
      project.stateStore.save()
    }
  }

  @Test
  @Suppress("DEPRECATION")
  fun `newModule should always create a new module from scratch`() = runBlocking {
    val moduleFile = tempDirManager.createVirtualFile("test.iml", "<module type=\"JAVA_MODULE\" foo=\"bar\" version=\"4\" />")
    projectRule.createModule(moduleFile.toNioPath()).useAndDispose {
      assertThat(getOptionValue("foo")).isNull()
    }
  }

  @Test
  fun `must be empty if classpath storage`() {
    Assume.assumeTrue("eclipse plugin is not found in classpath",
                      ClasspathStorageProvider.EXTENSION_POINT_NAME.extensionList.any { it.id == "eclipse" })
    runBlocking<Unit> {
      // we must not use VFS here, file must not be created
      val moduleFile = tempDirManager.newPath("module", refreshVfs = true).resolve("test.iml")
      projectRule.createModule(moduleFile).useAndDispose {
        ModuleRootModificationUtil.addContentRoot(this, moduleFile.parent.invariantSeparatorsPathString)
        project.stateStore.save()
        assertThat(moduleFile).isRegularFile
        assertThat(Strings.convertLineSeparators(moduleFile.readText())).startsWith("""
        <?xml version="1.0" encoding="UTF-8"?>
        <module type="JAVA_MODULE" version="4">""".trimIndent())

        ClasspathStorage.setStorageType(ModuleRootManager.getInstance(this), "eclipse")
        project.stateStore.save()
        assertThat(moduleFile).isEqualTo("""
        <?xml version="1.0" encoding="UTF-8"?>
        <module classpath="eclipse" classpath-dir="$ESCAPED_MODULE_DIR" type="JAVA_MODULE" version="4" />""")
      }
    }
  }

  @Test
  fun `one batch update session if several modules changed`(): Unit = runBlocking {
    val nameToCount = Object2IntOpenHashMap<String>()
    val root = tempDirManager.newPath()

    fun addContentRoot(module: Module) {
      val moduleName = module.name
      module.project.messageBus.connect(module).subscribe(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
        override fun onBatchUpdateStarted() {
          nameToCount.addTo(moduleName, 1)
        }
      })

      ModuleRootModificationUtil.addContentRoot(module, root.resolve(moduleName).invariantSeparatorsPathString)
      assertThat(module.contentRootUrls).hasSize(1)
    }

    fun removeContentRoot(module: Module) {
      val moduleFile = module.stateStore.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)
      assertThat(moduleFile).isRegularFile

      val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(moduleFile)!!
      val oldText = moduleFile.readText()
      val newText = oldText.replace("""<content url="file://${"$"}MODULE_DIR$/${module.name}" />""", "")
      assertThat(oldText).isNotEqualTo(newText)
      ApplicationManager.getApplication().runWriteAction {
        virtualFile.setBinaryContent(newText.toByteArray())
      }
    }

    fun assertChangesApplied(module: Module) {
      assertThat(module.contentRootUrls).isEmpty()
    }

    val m1 = projectRule.createModule(root.resolve("m1.iml"))
    val m2 = projectRule.createModule(root.resolve("m2.iml"))

    projectRule.project.messageBus.connect(m1).subscribe(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
      override fun onBatchUpdateStarted() {
        nameToCount.addTo("p", 1)
      }
    })

    addContentRoot(m1)
    addContentRoot(m2)
    projectRule.project.stateStore.save()

    withContext(Dispatchers.EDT) {
      removeContentRoot(m1)
      removeContentRoot(m2)
    }

    StoreReloadManager.getInstance(projectRule.project).reloadChangedStorageFiles()

    assertChangesApplied(m1)
    assertChangesApplied(m2)

    assertThat(nameToCount.size).isEqualTo(3)
    assertThat(nameToCount.getInt("p")).isEqualTo(1)
    assertThat(nameToCount.getInt("m1")).isEqualTo(1)
    assertThat(nameToCount.getInt("m1")).isEqualTo(1)
  }

  @Test
  fun `non-persistent module`() = runBlocking {
    val iprFileContent = """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project version="4">
      |</project>""".trimMargin()

    val projectCreator: (VirtualFile) -> Path = {
      it.writeChild("misc.xml", iprFileContent)
      Path.of(it.path)
    }

    loadAndUseProjectInLoadComponentStateMode(tempDirManager, projectCreator) { project ->
      // creating a persistent module to make non-empty valid modules.xml
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction {
          ModuleManager.getInstance(project).newModule(tempDirManager.newPath().resolve("persistent.iml"), JAVA_MODULE_ENTITY_TYPE_ID_NAME)
        }
      }
      project.stateStore.save()

      assertThat(project.isDirectoryBased).isTrue()
      val modulesFilePath = project.stateStore.directoryStorePath!!.resolve("modules.xml")
      val modulesFileAtStart = modulesFilePath.readText()

      val moduleName = "tmp-module-${Ksuid.generate()}"
      val contentRoot = tempDirManager.createVirtualDir()


      val module = edtWriteAction {
        ModuleManager.getInstance(project).newNonPersistentModule(moduleName, JAVA_MODULE_ENTITY_TYPE_ID_NAME)
      }
      withContext(Dispatchers.EDT) {
        SoftAssertions.assertSoftly {
          it.assertThat(module.moduleFilePath).isEmpty()
          it.assertThat(module.isLoaded).isTrue
          it.assertThat(module.moduleFile).isNull()
          it.assertThat(module.name).isEqualTo(moduleName)
        }

        ModuleRootModificationUtil.addContentRoot(module, contentRoot)
      }
      project.stateStore.save()

      val modulesFileAtEnd = modulesFilePath.readText()

      SoftAssertions.assertSoftly {
        it.assertThat(modulesFileAtEnd).isEqualTo(modulesFileAtStart)
        it.assertThat(ProjectRootManager.getInstance(project).contentRootUrls).contains("file://${contentRoot.path}")
      }
    }
  }

  @Test
  fun `do not fix format of iml if nothing was changed`() = runBlocking {
    val testData = Path.of(PathManagerEx.getCommunityHomePath(), "platform/configuration-store-impl/testData/moduleWithBlankLineAtEnd")
    val imlFileText = testData.resolve("module.iml").readText()
    val projectCreator: (VirtualFile) -> Path = {
      it.writeChild(".idea/modules.xml", testData.resolve(".idea/modules.xml").readText())
      it.writeChild("module.iml", imlFileText)
      Path.of(it.path)
    }

    loadAndUseProjectInLoadComponentStateMode(tempDirManager, projectCreator) { project ->
      project.stateStore.save()
      val moduleFilePath = Path.of(project.basePath!!).resolve("module.iml")
      assertThat(moduleFilePath.readText()).isEqualTo(imlFileText)
    }
  }
}

suspend inline fun <T> Module.useAndDispose(task: Module.() -> T): T {
  try {
    return task()
  }
  finally {
    withContext(Dispatchers.EDT) {
      ModuleManager.getInstance(project).disposeModule(this@useAndDispose)
    }
  }
}

suspend fun ProjectRule.loadModule(file: VirtualFile): Module {
  val project = project
  return edtWriteAction { ModuleManager.getInstance(project).loadModule(file.toNioPath()) }
}

val Module.contentRootUrls: Array<String>
  get() = ModuleRootManager.getInstance(this).contentRootUrls

internal suspend fun ProjectRule.createModule(path: Path): Module {
  val project = project
  return edtWriteAction {
    ModuleManager.getInstance(project).newModule(path, JAVA_MODULE_ENTITY_TYPE_ID_NAME)
  }
}
