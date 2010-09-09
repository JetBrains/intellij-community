package org.jetbrains.jps

/**
 * @author nik
 */
class ResourceCopyingTest extends JpsBuildTestCase {
  public void testOverwriteArtifacts() throws Exception {
    doTest("testData/resourceCopying/resourceCopying.ipr", {}, {
      dir("production") {
        dir("resourceCopying") {
          dir("copy") {
            file("file.txt")
          }
          dir("copyTree") {
            dir("subdir") {
              file("abc.txt")
            }
            file("xyz.txt")
          }
          file("a.txt")
          file("index.html")
        }
      }
    })
  }
}
