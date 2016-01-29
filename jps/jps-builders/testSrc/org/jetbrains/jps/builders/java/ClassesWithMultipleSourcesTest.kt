/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java

import org.jetbrains.jps.builders.JpsBuildTestCase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.application.ex.PathManagerEx
import java.io.File
import java.util.ArrayList
import org.jetbrains.jps.builders.BuildResult

/**
 * @author nik
 */
class ClassesWithMultipleSourcesTest: JpsBuildTestCase() {
  fun testAddFile() {
    doTest {
      createFile("src/a.p")
      modify {
        createFile("src/b.p")
      }
    }
  }

  fun testDeleteFile() {
    doTest {
      createFile("src/a.p")
      createFile("src/b.p")
      modify {
        deleteFile("src/b.p")
      }
    }
  }

  fun testDeletePackage() {
    doTest {
      createFile("src/a.p")
      modify {
        deleteFile("src/a.p")
      }
    }
  }

  fun testAddPackage() {
    doTest {
      modify {
        createFile("src/a.p")
      }
    }
  }

  fun testChangeFile() {
    doTest {
      createFile("src/a.p")
      modify {
        changeFile("src/a.p")
      }
    }
  }

  fun testChangeTargetPackage() {
    doTest {
      createFile("src/a.p")
      createFile("src/b.p")
      modify {
        changeFile("src/b.p", "package xxx;")
      }
    }
  }

  private fun doTest(actions: BuildTestActions.() -> Unit) {
    addModule("m", createDir("src"))
    val testActions = BuildTestActions()
    testActions.actions()
    rebuildAll()
    var result: BuildResult? = null
    testActions.modifyActions.forEach { action ->
      action()
      result = makeAll()
      result!!.assertSuccessful()
    }
    checkLog()
    checkMappingsAreSameAfterRebuild(result)
  }

  override fun getTestDataRootPath(): String {
    return FileUtil.toCanonicalPath(PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/incremental/multipleSources").absolutePath, '/')
  }

  private fun checkLog() {
    val testName = getTestName(true)
    checkFullLog(File(testDataRootPath, "$testName.log"))
  }

  private class BuildTestActions {
    val modifyActions = ArrayList<() -> Unit>()

    fun modify(action: () -> Unit) {
      modifyActions.add(action)
    }
  }
}