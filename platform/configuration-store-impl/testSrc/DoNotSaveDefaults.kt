package com.intellij.configurationStore

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.delete
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

private val testData: Path
  get() = Paths.get(PathManagerEx.getHomePath(DoNotSaveDefaultsTest::class.java), FileUtil.toSystemDependentName("platform/configuration-store-impl/testSrc"))

class DoNotSaveDefaultsTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()

    init {
      Paths.get(PathManager.getConfigPath()).delete()
    }
  }

  @Test
  fun testApp() {
    doTest(ApplicationManager.getApplication() as ApplicationImpl)
  }

  @Test
  fun testProject() {
    doTest(projectRule.project as ProjectImpl)
  }

  private fun doTest(componentManager: ComponentManagerImpl) {
    val useModCountOldValue = System.getProperty("store.save.use.modificationCount")

    // wake up
    val appPicoContainer = componentManager.picoContainer
    ServiceManagerImpl.processAllImplementationClasses(componentManager, { clazz, _ ->
      appPicoContainer.getComponentInstance(clazz.name)
      true
    })

    // <property name="file.gist.reindex.count" value="54" />
    val propertyComponent = PropertiesComponent.getInstance()
    propertyComponent.unsetValue("file.gist.reindex.count")
    // <property name="CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_2" value="1.0" />
    propertyComponent.unsetValue("CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_2")

    val app = ApplicationManager.getApplication() as ApplicationImpl
    try {
      System.setProperty("store.save.use.modificationCount", "false")
      app.doNotSave(false)
      runInEdtAndWait {
        StoreUtil.save(componentManager.stateStore, null)
      }
    }
    finally {
      System.setProperty("store.save.use.modificationCount", useModCountOldValue ?: "false")
      app.doNotSave(true)
    }

    val directoryTree = printDirectoryTree(Paths.get(componentManager.stateStore.stateStorageManager.expandMacros(if (componentManager === app) APP_CONFIG else PROJECT_CONFIG_DIR)), setOf(
      "path.macros.xml" /* todo EP to register (provide) macro dynamically */,
      "stubIndex.xml" /* low-level non-roamable stuff */,
      "usage.statistics.xml" /* SHOW_NOTIFICATION_ATTR in internal mode */,
      "feature.usage.statistics.xml" /* non-roamable usage counters */,
      "tomee.extensions.xml", "jboss.extensions.xml",
      "glassfish.extensions.xml" /* javaee non-roamable stuff, it will be better to fix it */,
      "dimensions.xml" /* non-roamable sizes of window, dialogs, etc. */,
      "debugger.renderers.xml", "debugger.xml" /* todo */,
      "databaseSettings.xml"
    ))
    println(directoryTree)
    assertThat(directoryTree).toMatchSnapshot(testData.resolve("DoNotSaveDefaults.snap.txt"))
  }
}

