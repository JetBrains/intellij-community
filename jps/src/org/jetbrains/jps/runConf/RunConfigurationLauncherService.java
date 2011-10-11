package org.jetbrains.jps.runConf;

import org.jetbrains.jps.ProjectBuilder;
import org.jetbrains.jps.RunConfiguration;
import org.jetbrains.jps.idea.OwnServiceLoader;

import java.util.Iterator;

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

  private static OwnServiceLoader<RunConfigurationLauncherService> runConfLauncherServices = OwnServiceLoader.load(RunConfigurationLauncherService.class);

  public static RunConfigurationLauncherService getLauncher(RunConfiguration runConfiguration) {
    final Iterator<RunConfigurationLauncherService> iterator = runConfLauncherServices.iterator();
    while (iterator.hasNext()) {
      RunConfigurationLauncherService service = iterator.next();
      if (service.typeId.equals(runConfiguration.getType())) {
        return service;
      }
    }

    return null;
  }
}
