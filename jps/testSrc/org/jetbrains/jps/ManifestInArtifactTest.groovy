package org.jetbrains.jps

import java.util.jar.Manifest
import org.jetbrains.jps.util.ZipUtil
import java.util.jar.Attributes

/**
 * @author nik
 */
class ManifestInArtifactTest extends JpsBuildTestCase {
  public void test() {
    def projectBuilder = buildAll("testData/manifestInArtifact/manifest.ipr", [:], null)
    File jarFile = new File(projectBuilder.targetFolder + "/artifacts/simple/simple.jar")
    assertTrue(jarFile.exists())
    File extracted = ZipUtil.extractToTempDir(jarFile)
    File manifestFile = new File(extracted, "META-INF/MANIFEST.MF")
    assertTrue(manifestFile.exists())
    Manifest manifest = new Manifest(new FileInputStream(manifestFile))
    assertEquals("MyClass", manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS))
  }
}
