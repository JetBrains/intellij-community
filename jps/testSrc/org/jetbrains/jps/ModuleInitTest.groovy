package org.jetbrains.jps

class ModuleInitTest extends JpsBuildTestCase {
  public void testBasePath() {
    Project project = loadProject("testData/moduleCycle/moduleCycle.ipr", [:]);
    for (def name: ['module1', 'module2']) {
      assertTrue(project.modules[name].basePath.endsWith("/" + name));
    }
  }
}
