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

/**
 * @author nik
 */
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
    doTest {
      createFile("src/a.p")
      createFile("src/b.p")
      modify {
        changeFile("src/b.p", "package xxx;")
      }
    }
  }

}