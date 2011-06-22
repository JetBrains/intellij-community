package org.jetbrains.jps

/**
 * @author pavel.sher
 */
class RunConfLoaderTest extends JpsBuildTestCase {
  public void testLoadRunConfigurationsIpr() {
    Project project = loadProject("testData/runConfigurationsIpr/maven-watcher.ipr", [:]);
    assertEquals(2, project.runConfigurations.size());
    assertNotNull(project.runConfigurations["maven-watcher-core"]);
    assertNotNull(project.runConfigurations["testng maven-watcher-core"]);

    RunConfiguration junitRunConf = project.runConfigurations["maven-watcher-core"];
    assertEquals("JUnit", junitRunConf.type);
    assertEquals("com.jetbrains.maven.watcher", junitRunConf.allOptions["PACKAGE_NAME"]);
    assertEquals(project.modules["maven-watcher-core"], junitRunConf.module)
  }

  public void testLoadRunConfigurationsDir() {
    Project project = loadProject("testData/runConfigurationsDir", [:]);
    assertEquals(2, project.runConfigurations.size());
    assertNotNull(project.runConfigurations["all tests"]);
    assertNotNull(project.runConfigurations["Web"]);
  }
}
