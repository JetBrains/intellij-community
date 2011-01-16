package org.jetbrains.jps

class JavacSettingsTest extends JpsBuildTestCase {
  public void testLoadJavacSettings() throws Exception {
    Project project = loadProject("testData/resourceCopying/resourceCopying.ipr", [:]);
    assertNotNull(project.props["compiler.javac.options"])
    assertEquals("512", project.props["compiler.javac.options"]["MAXIMUM_HEAP_SIZE"])
    assertEquals("false", project.props["compiler.javac.options"]["DEBUGGING_INFO"])
    assertEquals("true", project.props["compiler.javac.options"]["GENERATE_NO_WARNINGS"])
  }
}
