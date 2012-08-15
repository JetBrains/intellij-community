package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.concurrent.Future;

/**
 * @author nik
 */
public abstract class JdkVersionDetector {
  public static JdkVersionDetector getInstance() {
    return JpsServiceManager.getInstance().getService(JdkVersionDetector.class);
  }

  @Nullable
  public abstract String detectJdkVersion(String homePath);

  @Nullable
  public abstract String detectJdkVersion(String homePath, ActionRunner actionRunner);

  @Nullable
  public abstract String readVersionFromProcessOutput(String homePath, String[] command, String versionLineMarker,
                                                      ActionRunner actionRunner);

  //todo[nik] replace with a service with difference implementation for IDEA and for JPS process (need to exclude jps-builders module from IDEA classpath)
  public interface ActionRunner {
    Future<?> run(Runnable runnable);
  }
}
