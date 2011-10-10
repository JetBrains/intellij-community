package org.jetbrains.jps

import org.jetbrains.jps.util.FileUtil

/**
 * @author nik
 */
class ArtifactWithoutOutputTest extends JpsBuildTestCase {
  public void test() throws Exception {
    def outDir = FileUtil.createTempDirectory("output").absolutePath
    Project project = loadProject("testData/artifactWithoutOutput/artifactWithoutOutput.ipr", ["OUTPUT_DIR":outDir])
    ProjectBuilder builder = createBuilder(project)
    builder.tempFolder = FileUtil.createTempDirectory("tmp").absolutePath
    builder.clean()
    builder.buildArtifact("main")
    builder.deleteTempFiles()
    assertOutput(outDir, {
      dir("artifacts") {
        dir("main") {
          file("data.txt")
          file("data2.txt")
        }
      }
    })
  }
}
