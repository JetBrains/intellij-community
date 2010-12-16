package org.jetbrains.jps

import org.jetbrains.jps.util.FileUtil

 /**
 * @author nik
 */
public class CleanArchiveArtifactTest extends JpsBuildTestCase {
  public void test() throws Exception {
    def outDir = FileUtil.createTempDirectory("output").absolutePath
    Project project = loadProject("testData/cleanArchiveArtifact/cleanArchiveArtifact.ipr", ["OUTPUT_DIR":outDir])
    project.tempFolder = FileUtil.createTempDirectory("tmp").absolutePath
    project.clean()
    project.buildArtifact("jar")
    project.deleteTempFiles()
    assertOutput(project, outDir) {
      archive("jar.jar") {
        dir("META-INF") {
          file("MANIFEST.MF")
        }
        file("a.txt")
      }
    }

    project.clean()
    assertOutput(project, outDir) {
    }
  }
}
