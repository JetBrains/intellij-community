package org.jetbrains.jps

/**
 * @author nik
 */
class ArtifactIncludesArchiveArtifactTest extends JpsBuildTestCase {
  public void test() throws Exception {
    def name = "artifactIncludesArchiveArtifact"
    def projectBuilder = buildAll("testData/$name/${name}.ipr", [:], {Project project, ProjectBuilder projectBuilder ->
      projectBuilder.targetFolder = null
    })
    try {
      assertOutput("testData/$name/out", {
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
      projectBuilder.clean()
    }
  }
}
