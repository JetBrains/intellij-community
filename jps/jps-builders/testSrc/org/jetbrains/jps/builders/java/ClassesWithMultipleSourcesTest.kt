// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java

class ClassesWithMultipleSourcesTest: IncrementalBuildTestCase() {
  override val testDataDirectoryName: String
    get() = "multipleSources"

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
    // now relevant for the new implementation only
    if (JavaBuilderUtil.isDepGraphEnabled()) { // todo: remove the check when the new dep-graph based implementation becomes the default one
      doTest {
        createFile("src/a.p")
        createFile("src/b.p")
        modify {
          changeFile("src/b.p", "package xxx;")
        }
      }
    }
  }

}