package org.jetbrains.jps
import junit.framework.TestCase

class AntPrePostStepsInArtifactsTest extends TestCase {
  //todo[nik] move to ant plugin and fix
  public void test() throws Exception {
    //Project project = loadProject("testData/artifactWithAntPrePostTasks/.idea", [:])
    //builder.tempFolder = FileUtil.createTempDirectory("ant-jps-artifacts", "").absolutePath
    //builder.clean()
    //builder.buildArtifact("main")
    //builder.deleteTempFiles()
    //File outDir = new File("testData/artifactWithAntPrePostTasks/out");
    //assertOutput(outDir.getAbsolutePath(), {
    //  dir("artifacts") {
    //    dir("main") {
    //      dir("dir") {
    //        file("file.txt")
    //      }
    //      file("prestep.txt", "pre1")
    //      file("poststep.txt", "${com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(outDir.absolutePath)}/artifacts/main")
    //    }
    //  }
    //})
  }

  public void test_broken_artifact() throws Exception {
    //Project project = loadProject("testData/artifactWithAntPrePostTasks/.idea", [:])
    //assertEquals(2, project.artifacts.size());
    //for (Artifact a: project.artifacts.values()) {
    //  assertNull(project.artifacts.properties);
    //}
  }
}
