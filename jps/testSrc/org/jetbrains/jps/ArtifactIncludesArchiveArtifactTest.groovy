package org.jetbrains.jps

/**
 * @author nik
 */
class ArtifactIncludesArchiveArtifactTest extends JpsBuildTestCase {
  public void test() throws Exception {
    def name = "artifactIncludesArchiveArtifact"
    def project = buildAll("testData/$name/${name}.ipr", [:], {Project project ->
      project.targetFolder = null
    })
    try {
      assertOutput(project, "testData/$name/out", {
        dir("artifacts") {
          dir("data") {
            archive("a.jar") {
              dir("META-INF") {
                file("MANIFEST.MF")
              }
              file("a.txt")
            }
          }
        }
      })
    }
    finally {
      project.clean()
    }
  }
}
