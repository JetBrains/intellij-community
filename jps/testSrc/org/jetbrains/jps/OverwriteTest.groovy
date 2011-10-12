package org.jetbrains.jps

/**
 * @author nik
 */
class OverwriteTest extends JpsBuildTestCase {
  public void testOverwriteArtifacts() throws Exception {
    doTest("testData/overwriteTest/overwriteTest.ipr", null, {
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
}
