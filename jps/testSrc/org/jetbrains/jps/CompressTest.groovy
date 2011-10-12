package org.jetbrains.jps

/**
 * @author nik
 */
class CompressTest extends JpsBuildTestCase {
  public void testCompress() throws Exception {
    File jarFile = doBuildJar(null)
    assertTrue(jarFile.size() < 1000)
  }

  public void testNotCompress() throws Exception {
    File jarFile = doBuildJar {Project project, ProjectBuilder builder ->
      builder.compressJars = false
    }
    assertTrue(jarFile.size() > 5000)
  }

  private File doBuildJar(Closure initProject) {
    ProjectBuilder projectBuilder = buildAll("testData/compressTest/compressTest.ipr", [:], initProject)
    File jarFile = new File(projectBuilder.targetFolder + "/artifacts/data/data.jar")
    assertTrue("$jarFile.absolutePath doesn't exist", jarFile.exists())
    return jarFile
  }
}
