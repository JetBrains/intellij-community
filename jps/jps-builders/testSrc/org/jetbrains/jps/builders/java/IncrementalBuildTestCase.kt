// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.builders.BuildResult
import org.jetbrains.jps.builders.JpsBuildTestCase
import java.io.File

abstract class IncrementalBuildTestCase : JpsBuildTestCase() {
  protected fun doTest(checkMappingAfterRebuild: Boolean = true,
                       actions: BuildTestActions.() -> Unit) {
    addModule("m", createDir("src"))
    val testActions = BuildTestActions()
    testActions.actions()
    rebuildAllModules()
    var result: BuildResult? = null
    testActions.modifyActions.forEach { action ->
      action()
      result = buildAllModules()
      result!!.assertSuccessful()
    }
    checkLog()
    if (checkMappingAfterRebuild) {
      checkMappingsAreSameAfterRebuild(result)
    }
  }

  protected abstract val testDataDirectoryName: String

  override fun getTestDataRootPath(): String {
    return FileUtil.toCanonicalPath(
      PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/incremental/$testDataDirectoryName").absolutePath, '/')
  }

  private fun checkLog() {
    checkFullLog(File(testDataRootPath, "${getTestName(true)}.log"))
  }

  protected class BuildTestActions {
    val modifyActions = ArrayList<() -> Unit>()

    fun modify(action: () -> Unit) {
      modifyActions.add(action)
    }
  }
}