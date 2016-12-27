package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.delete
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Paths

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
    try {
      app.doNotSave(false)
      runInEdtAndWait {
        app.saveAll()
      }
    }
    finally {
      app.doNotSave(true)
    }

    println(directory)
    println(printDirectoryTree(dirPath.toFile()))
  }
}

