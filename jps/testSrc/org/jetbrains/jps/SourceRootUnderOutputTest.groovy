package org.jetbrains.jps

 /**
 * @author nik
 */
public class SourceRootUnderOutputTest extends JpsBuildTestCase {
  public void test() throws Exception {
    Project project = loadProject("testData/sourceFolderUnderOutput/sourceFolderUnderOutput.ipr", [:])
    try {
      project.clean()
      fail("Cleaning should fail")
    }
    catch (Exception e) {
    }
  }
}
