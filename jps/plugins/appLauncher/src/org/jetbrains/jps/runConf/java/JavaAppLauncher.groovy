package org.jetbrains.jps.runConf.java

import org.jetbrains.jps.RunConfiguration

class JavaAppLauncher extends JavaBasedRunConfigurationLauncher {
  public JavaAppLauncher() {
    super("Application")
  }

  @Override
  String getMainClassName(RunConfiguration runConf) {
    return runConf.allOptions["MAIN_CLASS_NAME"]
  }

  @Override
  String getMainClassArguments(RunConfiguration runConf) {
    return runConf.allOptions["PROGRAM_PARAMETERS"]
  }

  @Override
  List<String> getMainClassClasspath(RunConfiguration runConf) {
    return Collections.emptyList()
  }
}
