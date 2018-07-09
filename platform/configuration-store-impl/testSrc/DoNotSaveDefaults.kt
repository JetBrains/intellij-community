// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.delete
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

private val testData: Path
  get() = Paths.get(PathManagerEx.getHomePath(DoNotSaveDefaultsTest::class.java),
                    FileUtil.toSystemDependentName("platform/configuration-store-impl/testSrc"))

class DoNotSaveDefaultsTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()

    init {
      Paths.get(PathManager.getConfigPath()).delete()
    }
  }

  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()


  @Test
  fun testApp() {
    doTest(ApplicationManager.getApplication() as ApplicationImpl)
  }

  @Test
  fun testProject() {
    createOrLoadProject(tempDir, directoryBased = false) { project ->
      doTest(project as ProjectImpl)
    }
  }

  private fun doTest(componentManager: ComponentManagerImpl) {
    val useModCountOldValue = System.getProperty("store.save.use.modificationCount")

    // wake up (edt, some configurables want read action)
    runInEdtAndWait {
      val picoContainer = componentManager.picoContainer
      ServiceManagerImpl.processAllImplementationClasses(componentManager, { clazz, _ ->
        val className = clazz.name
        // CvsTabbedWindow calls invokeLater in constructor
        if (className != "com.intellij.cvsSupport2.ui.CvsTabbedWindow"
            && className != "com.intellij.lang.javascript.bower.BowerPackagingService"
            && className != "org.jetbrains.plugins.groovy.mvc.MvcConsole") {
          picoContainer.getComponentInstance(className)
        }
        true
      })
    }

    val propertyComponent = PropertiesComponent.getInstance()
    // <property name="file.gist.reindex.count" value="54" />
    propertyComponent.unsetValue("file.gist.reindex.count")
    // <property name="CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_2" value="1.0" />
    propertyComponent.unsetValue("CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_2")
    propertyComponent.unsetValue("ts.lib.d.ts.version")
    propertyComponent.unsetValue("nodejs_interpreter_path.stuck_in_default_project")

    val app = ApplicationManager.getApplication() as ApplicationImpl
    try {
      System.setProperty("store.save.use.modificationCount", "false")
      app.isSaveAllowed = true
      runInEdtAndWait {
        StoreUtil.save(componentManager.stateStore, null)
      }
    }
    finally {
      System.setProperty("store.save.use.modificationCount", useModCountOldValue ?: "false")
      app.isSaveAllowed = false
    }

    if (componentManager is Project) {
      assertThat(Paths.get(componentManager.projectFilePath!!)).doesNotExist()
      return
    }

    val directoryTree = printDirectoryTree(Paths.get(
      componentManager.stateStore.storageManager.expandMacros(APP_CONFIG)), setOf(
      "path.macros.xml" /* todo EP to register (provide) macro dynamically */,
      "stubIndex.xml" /* low-level non-roamable stuff */,
      UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML /* SHOW_NOTIFICATION_ATTR in internal mode */,
      "tomee.extensions.xml", "jboss.extensions.xml",
      "glassfish.extensions.xml" /* javaee non-roamable stuff, it will be better to fix it */,
      "dimensions.xml" /* non-roamable sizes of window, dialogs, etc. */,
      "debugger.renderers.xml", "debugger.xml" /* todo */,
      "databaseSettings.xml"
    ))
    println(directoryTree)
    assertThat(directoryTree).isEmpty()
  }
}

