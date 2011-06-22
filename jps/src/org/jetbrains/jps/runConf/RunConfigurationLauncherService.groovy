package org.jetbrains.jps.runConf

import org.jetbrains.jps.RunConfiguration

public abstract class RunConfigurationLauncherService {
  final String typeId

  RunConfigurationLauncherService(String typeId) {
    this.typeId = typeId
  }

  public void beforeStart(RunConfiguration runConf) {};

  public void afterFinish(RunConfiguration runConf) {};

  public final void startRunConfiguration(RunConfiguration runConf) {
    beforeStart(runConf);
    try {
      start(runConf);
    } finally {
      afterFinish(runConf);
    };
  };

  public abstract void start(RunConfiguration runConf);
}
