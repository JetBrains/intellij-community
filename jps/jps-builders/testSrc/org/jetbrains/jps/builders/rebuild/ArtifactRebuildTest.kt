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
package org.jetbrains.jps.builders.rebuild

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.directoryContent
import java.io.File
import java.io.FileInputStream
import java.util.jar.Attributes
import java.util.jar.Manifest

/**
 * @author nik
 */
class ArtifactRebuildTest: JpsRebuildTestCase() {
  fun testArtifactIncludesArchiveArtifact() {
    val name = "artifactIncludesArchiveArtifact"
    try {
      doTest("$name/${name}.ipr", directoryContent {
        dir("artifacts") {
          dir("data") {
            zip("a.jar") {
              file("a.txt")
            }
          }
        }
      })
    }
    finally {
      FileUtil.delete(File(FileUtil.toSystemDependentName(testDataRootPath + "/$name/data/a.jar")))
    }
  }

  fun testArtifactWithoutOutput() {
    val outDir = FileUtil.createTempDirectory("output", "").absolutePath
    loadProject("artifactWithoutOutput/artifactWithoutOutput.ipr", mapOf("OUTPUT_DIR" to outDir))

    rebuild()
    assertOutput(outDir, directoryContent {
      dir("artifacts") {
        dir("main") {
          file("data.txt")
          file("data2.txt")
        }
      }
    })
  }

  fun testExtractDir() {
    doTest("extractDirTest/extractDirTest.ipr", directoryContent {
      dir("artifacts") {
        dir("extractDir") {
          file("b.txt", "b")
        }
        dir("extractRoot") {
          dir("extracted") {
            dir("dir") {
              file("b.txt", "b")
            }
            file("a.txt", "a")
          }
        }
        dir("packedDir") {
          zip("packedDir.jar") {
            file("b.txt", "b")
          }
        }
        dir("packedRoot") {
          zip("packedRoot.jar") {
            dir("dir") {
              file("b.txt", "b")
            }
            file("a.txt", "a")
          }
        }
      }
    })
  }

  fun testManifestInArtifact() {
    loadAndRebuild("manifestInArtifact/manifest.ipr", mapOf())
    val jarFile = File(myOutputDirectory, "artifacts/simple/simple.jar")
    assertTrue(jarFile.exists())
    val extracted = FileUtil.createTempDirectory("build-manifest", "")
    ZipUtil.extract(jarFile, extracted, null)
    val manifestFile = File(extracted, "META-INF/MANIFEST.MF")
    assertTrue(manifestFile.exists())
    val manifest = Manifest(FileInputStream(manifestFile))
    assertEquals("MyClass", manifest.mainAttributes!!.getValue(Attributes.Name.MAIN_CLASS))
  }

  fun testOverwriteArtifacts() {
    doTest("overwriteTest/overwriteTest.ipr", directoryContent {
      dir("artifacts") {
        dir("classes") {
          file("a.xml", "<root2/>")
        }
        dir("dirs") {
          file("x.txt", "d2")
        }
        dir("fileCopy") {
          dir("xxx") {
            dir("a") {
              file("f.txt", "b")
            }
          }
        }
      }
      dir("production") {
        dir("dep") {
          file("a.xml", "<root2/>")
        }
        dir("overwriteTest") {
          file("a.xml", "<root1/>")
        }
      }
    })
  }

  fun testPathVariablesInArtifact() {
    val externalDir = "${testDataRootPath}/pathVariables/external"
    doTest("pathVariables/pathVariables.ipr", mapOf("EXTERNAL_DIR" to externalDir), directoryContent {
      dir("artifacts") {
        dir("fileCopy") {
          dir("dir") {
            file("file.txt", "xxx")
          }
        }
      }
    })
  }

  fun testModuleTestOutputElement() {
    doTest("moduleTestOutput/moduleTestOutput.ipr", directoryContent {
      dir("artifacts") {
        dir("tests") {
          file("MyTest.class")
        }
      }
      dir("production") {
        dir("moduleTestOutput") {
          file("MyClass.class")
        }
      }
      dir("test") {
        dir("moduleTestOutput") {
          file("MyTest.class")
        }
      }
    })
  }
}
