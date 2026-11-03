// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacet
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.ProjectBulkFileListener
import com.intellij.openapi.vfs.ProjectBulkFileListenerRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.settings.SettingsController
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInLoadComponentStateMode
import com.intellij.testFramework.useProjectAsync
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class FileBasedStorageTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @TempDir
  private lateinit var projectDir: Path

  @Test
  fun `saving a new file reports only storage write events`(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    withProject { project, _, _ ->
      val file = projectDir.resolve(".idea/test.iml")
      val observed = CopyOnWriteArrayList<VFileEvent>()
      ProjectBulkFileListenerRegistry.getInstance(project).addListener(object : ProjectBulkFileListener {
        override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
          observed.addAll(events.filter { it.path == file.invariantSeparatorsPathString })
        }
      }, disposable)

      val storage = object : FileBasedStorage(file = file, fileSpec = "test.iml", rootElementName = "module", roamingType = RoamingType.DISABLED) {
        override val controller: SettingsController? = null
      }
      val session = storage.createSaveSessionProducer()!!
      session.setState(component = null, componentName = "test", pluginId = PluginManagerCore.CORE_ID,
                       state = Element("state").setAttribute("value", "test"))
      val events = ArrayList<VFileEvent>()
      session.createSaveSession()!!.save(events)

      assertThat(observed).isEmpty()
      RefreshQueue.getInstance().processEvents(events)
      assertThat(observed).hasSize(1)
      assertThat(observed.single()).isInstanceOf(VFileCreateEvent::class.java)
      assertThat(observed.single().requestor).isInstanceOf(StorageManagerFileWriteRequestor::class.java)
      assertThat(VirtualFileManager.getInstance().findFileByNioPath(file)).isNotNull()
    }
  }

  @Test
  fun `the first save preserves newer facet configuration and external changes still reload`(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    registerFacetType(MockFacetType(), disposable)
    withProject { project, module, root ->
      val manager = FacetManager.getInstance(module)
      val facet = MockFacet(module, "test")
      edtWriteAction {
        manager.createModifiableModel().apply {
          addFacet(facet)
          commit()
        }
      }

      val changedDuringSave = AtomicBoolean()
      val directoryPath = projectDir.resolve(".idea").invariantSeparatorsPathString
      ProjectBulkFileListenerRegistry.getInstance(project).addListener(object : ProjectBulkFileListener {
        override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
          if (events.any { it is VFileCreateEvent && it.path == directoryPath } && changedDuringSave.compareAndSet(false, true)) {
            facet.configuration.data = "configured"
            manager.facetConfigurationChanged(facet)
          }
        }
      }, disposable)

      assertThat(saveSettings(project)).isTrue()
      assertThat(changedDuringSave).isTrue()
      val file = projectDir.resolve(".idea/test.iml")
      assertThat(file.readText()).doesNotContain("data=\"configured\"")

      StoreReloadManager.getInstance(project).reloadChangedStorageFiles()
      assertThat(manager.getFacetByType(MockFacetType.ID)!!.configuration.data).isEqualTo("configured")
      assertThat(saveSettings(project)).isTrue()
      assertThat(file.readText()).contains("data=\"configured\"")

      val xml = JDOMUtil.load(file)
      val configuration = xml.getChildren("component").single { it.getAttributeValue("name") == "FacetManager" }
        .getChild("facet").getChild("configuration")
      configuration.setAttribute("data", "external")
      file.writeText(JDOMUtil.write(xml))
      RefreshQueue.getInstance().refresh(recursive = true, files = listOf(root))
      StoreReloadManager.getInstance(project).reloadChangedStorageFiles()
      assertThat(manager.getFacetByType(MockFacetType.ID)!!.configuration.data).isEqualTo("external")
    }
  }

  private suspend fun withProject(action: suspend (Project, Module, VirtualFile) -> Unit) {
    val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectDir, OpenProjectTask {
      isNewProject = true
      createModule = false
      runConfigurators = false
      useDefaultProjectAsTemplate = false
    })!!
    project.useProjectAsync {
      project.runInLoadComponentStateMode {
        val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(projectDir)!!
        val module = edtWriteAction {
          ModuleManager.getInstance(project).newModule(projectDir.resolve(".idea/test.iml"), "JAVA_MODULE").also { module ->
            ModuleRootManager.getInstance(module).modifiableModel.apply {
              addContentEntry(root)
              commit()
            }
          }
        }
        assertThat(projectDir.resolve(".idea").exists()).isFalse()
        action(project, module, root)
      }
    }
  }
}
