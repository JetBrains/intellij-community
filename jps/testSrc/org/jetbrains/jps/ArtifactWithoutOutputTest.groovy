package org.jetbrains.jps

import org.jetbrains.jps.util.FileUtil

/**
 * @author nik
 */
class ArtifactWithoutOutputTest extends JpsBuildTestCase {
  public void test() throws Exception {
    def outDir = FileUtil.createTempDirectory("output").absolutePath
    Project project = loadProject("testData/artifactWithoutOutput/artifactWithoutOutput.ipr", ["OUTPUT_DIR":outDir])
    project.tempFolder = FileUtil.createTempDirectory("tmp").absolutePath
    project.clean()
    project.buildArtifact("main")
    project.deleteTempFiles()
    assertOutput(project, outDir) {
      dir("artifacts") {
        dir("main") {
          file("data.txt")
          file("data2.txt")
        }
      }
    }
  }
}
