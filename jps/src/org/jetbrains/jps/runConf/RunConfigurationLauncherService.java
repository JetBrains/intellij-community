package org.jetbrains.jps.runConf;

import org.jetbrains.jps.ProjectBuilder;
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

  public final void start(RunConfiguration runConf, ProjectBuilder projectBuilder) {
    beforeStart(runConf);
    try {
      startInternal(runConf, projectBuilder);
    } finally {
      afterFinish(runConf);
    }
  }

  protected abstract void startInternal(RunConfiguration runConf, ProjectBuilder projectBuilder);
}
