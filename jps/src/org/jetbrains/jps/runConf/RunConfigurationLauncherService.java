package org.jetbrains.jps.runConf;

import org.jetbrains.jps.RunConfiguration;

public abstract class RunConfigurationLauncherService {
  private final String typeId;

  public RunConfigurationLauncherService(String typeId) {
    this.typeId = typeId;
  }

  public String getTypeId() {
    return typeId;
  }

  public void beforeStart(RunConfiguration runConf) {}

  public void afterFinish(RunConfiguration runConf) {}

  public final void start(RunConfiguration runConf) {
    beforeStart(runConf);
    try {
      actualStart(runConf);
    } finally {
      afterFinish(runConf);
    }
  }

  protected abstract void actualStart(RunConfiguration runConf);
}
