package org.jetbrains.jps.builders

import org.jetbrains.jps.RunConfiguration
import org.jetbrains.jps.builders.rebuild.JpsRebuildTestCase
/**
 * @author pavel.sher
 */
class RunConfLoaderTest extends JpsRebuildTestCase {
  //todo[nik] fix
  public void xxxTestLoadRunConfigurationsIpr() {
    loadProject("runConfigurationsIpr/maven-watcher.ipr", [:]);
    assertEquals(2, myProject.runConfigurations.size());
    assertNotNull(myProject.runConfigurations["maven-watcher-core"]);
    assertNotNull(myProject.runConfigurations["testng maven-watcher-core"]);

    RunConfiguration junitRunConf = myProject.runConfigurations["maven-watcher-core"];
    assertEquals("JUnit", junitRunConf.type);
    assertEquals("com.jetbrains.maven.watcher", junitRunConf.allOptions["PACKAGE_NAME"]);
    def module = myJpsProject.modules.find {"maven-watcher".equals(it.name)}
    assertNotNull(module)
    assertEquals(module, junitRunConf.moduleRef.asExternal(myModel).resolve())
  }

  public void testLoadRunConfigurationsDir() {
    loadProject("runConfigurationsDir", [:]);
    assertEquals(2, myProject.runConfigurations.size());
    assertNotNull(myProject.runConfigurations["all tests"]);
    assertNotNull(myProject.runConfigurations["Web"]);
  }
}
