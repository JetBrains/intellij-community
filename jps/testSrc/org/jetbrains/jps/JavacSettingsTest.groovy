package org.jetbrains.jps

class JavacSettingsTest extends JpsBuildTestCase {
  public void testLoadJavacSettings() throws Exception {
    Project project = loadProject("testData/resourceCopying/resourceCopying.ipr", [:]);
    Map<String, String> options = project.compilerConfiguration.javacOptions
    assertNotNull(options)
    assertEquals("512", options["MAXIMUM_HEAP_SIZE"])
    assertEquals("false", options["DEBUGGING_INFO"])
    assertEquals("true", options["GENERATE_NO_WARNINGS"])
  }
}
