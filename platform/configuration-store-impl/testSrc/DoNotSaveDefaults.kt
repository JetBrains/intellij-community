package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.stateStore
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
  fun test() {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    val directory = app.stateStore.stateStorageManager.expandMacros(APP_CONFIG)
    val dirPath = Paths.get(directory)
    val useModCountOldValue = System.getProperty("store.save.use.modificationCount")

    // wake up
    ServiceManagerImpl.processAllImplementationClasses(app, { clazz, pluginDescriptor ->
      app.picoContainer.getComponentInstance(clazz.name)
      true
    })

    try {
      System.setProperty("store.save.use.modificationCount", "false")
      app.doNotSave(false)
      runInEdtAndWait {
        app.saveAll()
      }
    }
    finally {
      System.setProperty("store.save.use.modificationCount", useModCountOldValue ?: "false")
      app.doNotSave(true)
    }

    println(directory)
    val directoryTree = printDirectoryTree(dirPath, setOf(
      "path.macros.xml" /* todo EP to register (provide) macro dynamically */,
      "stubIndex.xml" /* low-level non-roamable stuff */
    ))
    println(directoryTree)
    assertThat(directoryTree).toMatchSnapshot(testData.resolve("DoNotSaveDefaults.snap.txt"))
  }
}

