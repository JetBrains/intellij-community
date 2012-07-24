package org.jetbrains.jps.builders.rebuild

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil

import java.util.jar.Attributes
import java.util.jar.Manifest
/**
 * @author nik
 */
class ArtifactRebuildTest extends JpsRebuildTestCase {
  public void testArtifactIncludesArchiveArtifact() {
    def name = "artifactIncludesArchiveArtifact"
    try {
      doTest("$name/${name}.ipr", null) {
        dir("artifacts") {
          dir("data") {
            archive("a.jar") {
              file("a.txt")
            }
          }
        }
      }
    }
    finally {
      FileUtil.delete(new File(FileUtil.toSystemDependentName(getTestDataRootPath() + "/$name/data/a.jar")))
    }
  }

  public void testArtifactWithoutOutput() {
    def outDir = FileUtil.createTempDirectory("output", "").absolutePath
    loadProject("artifactWithoutOutput/artifactWithoutOutput.ipr", ["OUTPUT_DIR":outDir])

    rebuild()
    assertOutput(outDir, {
      dir("artifacts") {
        dir("main") {
          file("data.txt")
          file("data2.txt")
        }
      }
    })
  }

  public void testExtractDir() {
    doTest("extractDirTest/extractDirTest.ipr", null, {
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
          archive("packedDir.jar") {
            file("b.txt", "b")
          }
        }
        dir("packedRoot") {
          archive("packedRoot.jar") {
            dir("dir") {
              file("b.txt", "b")
            }
            file("a.txt", "a")
          }
        }
      }
    })
  }

  public void testManifestInArtifact() {
    loadAndRebuild("manifestInArtifact/manifest.ipr", [:], null)
    File jarFile = new File(myOutputDirectory, "artifacts/simple/simple.jar")
    junit.framework.Assert.assertTrue(jarFile.exists())
    File extracted = FileUtil.createTempDirectory("build-manifest", "")
    ZipUtil.extract(jarFile, extracted, null)
    File manifestFile = new File(extracted, "META-INF/MANIFEST.MF")
    junit.framework.Assert.assertTrue(manifestFile.exists())
    Manifest manifest = new Manifest(new FileInputStream(manifestFile))
    junit.framework.Assert.assertEquals("MyClass", manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS))
  }

  public void testOverwriteArtifacts() {
    doTest("overwriteTest/overwriteTest.ipr", null, {
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

  public void testPathVariablesInArtifact() {
    String externalDir = "${getTestDataRootPath()}/pathVariables/external"
    doTest("pathVariables/pathVariables.ipr", ["EXTERNAL_DIR": externalDir], null, {
      dir("artifacts") {
        dir("fileCopy") {
          dir("dir") {
            file("file.txt", "xxx")
          }
        }
      }
    })
  }

  public void testModuleTestOutputElement() {
    doTest("moduleTestOutput/moduleTestOutput.ipr", null, {
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

  //todo[nik] fix
  public void _testSourceRootUnderOutput() throws Exception {
    loadProject("sourceFolderUnderOutput/sourceFolderUnderOutput.ipr", [:])
    try {
      rebuild()
      junit.framework.Assert.fail("Cleaning should fail")
    }
    catch (Exception ignored) {
    }
  }
}
