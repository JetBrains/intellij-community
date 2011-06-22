package org.jetbrains.jps.runConf.java

import org.jetbrains.jps.JpsBuildTestCase
import org.jetbrains.jps.Project
import org.jetbrains.jps.RunConfiguration
import org.jetbrains.jps.idea.OwnServiceLoader
import org.jetbrains.jps.runConf.RunConfigurationLauncherService
import org.jetbrains.jps.util.FileUtil

class JavaAppLauncherTest extends JpsBuildTestCase {
  public void test_simple() {
    Project project = loadProject("plugins/appLauncher/testData/main-class-run-conf", [:]);

    RunConfiguration runConf = project.runConfigurations["MainClass"];

    project.targetFolder = createTempDir().absolutePath;

    project.makeAll();

    File outFile = createTempFile();

    JavaAppLauncher launcher = getLauncher();
    launcher.setOutputFile(outFile);

    launcher.start(runConf);

    String output = FileUtil.loadFileText(outFile);
    assertTrue(output, output.indexOf("arg1" + System.getProperty("line.separator")) != -1)
    assertTrue(output, output.indexOf("arg2") != -1)
  }

  private JavaAppLauncher getLauncher() {
    for (RunConfigurationLauncherService service: OwnServiceLoader.load(RunConfigurationLauncherService.class).iterator()) {
      if (service instanceof JavaAppLauncher) return service;
    }

    return null;
  }

}
