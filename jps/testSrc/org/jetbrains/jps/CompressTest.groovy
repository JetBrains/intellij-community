package org.jetbrains.jps

/**
 * @author nik
 */
class CompressTest extends JpsBuildTestCase {
  public void testCompress() throws Exception {
    File jarFile = doBuildJar({})
    assertTrue(jarFile.size() < 1000)
  }

  public void testNotCompress() throws Exception {
    File jarFile = doBuildJar {Project project ->
      project.builder.compressJars = false
    }
    assertTrue(jarFile.size() > 5000)
  }

  private File doBuildJar(Closure initProject) {
    Project project = buildAll("testData/compressTest/compressTest.ipr", [:], initProject)
    File jarFile = new File(project.targetFolder + "/artifacts/data/data.jar")
    assertTrue("$jarFile.absolutePath doesn't exist", jarFile.exists())
    return jarFile
  }
}
