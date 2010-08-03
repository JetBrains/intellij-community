package org.jetbrains.jps

/**
 * @author nik
 */
class ExtractedDirectoryElementTest extends JpsBuildTestCase {
  public void testExtractDir() throws Exception {
    doTest("testData/extractDirTest/extractDirTest.ipr", {}, {
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
            dir("META-INF") { file("MANIFEST.MF") }
            file("b.txt", "b")
          }
        }
        dir("packedRoot") {
          archive("packedRoot.jar") {
            dir("META-INF") { file("MANIFEST.MF") }
            dir("dir") {
              file("b.txt", "b")
            }
            file("a.txt", "a")
          }
        }
      }
    })
  }
}
