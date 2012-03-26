package org.jetbrains.jps.server;

import org.jetbrains.jps.JavaSdk;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.Sdk;
import org.jetbrains.jps.incremental.BuildLoggingManager;
import org.jetbrains.jps.incremental.FSState;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*         Date: 1/8/12
*/
public final class ProjectDescriptor {
  public final Project project;
  public final FSState fsState;
  public final ProjectTimestamps timestamps;
  public final BuildDataManager dataManager;
  private final BuildLoggingManager myLoggingManager;
  public ModuleRootsIndex rootsIndex;
  private int myUseCounter = 1;
  private Set<JavaSdk> myProjectJavaSdks;

  public ProjectDescriptor(Project project,
                           FSState fsState,
                           ProjectTimestamps timestamps,
                           BuildDataManager dataManager,
                           BuildLoggingManager loggingManager) {
    this.project = project;
    this.fsState = fsState;
    this.timestamps = timestamps;
    this.dataManager = dataManager;
    myLoggingManager = loggingManager;
    this.rootsIndex = new ModuleRootsIndex(project);
    myProjectJavaSdks = new HashSet<JavaSdk>();
    for (Module module : project.getModules().values()) {
      final Sdk sdk = module.getSdk();
      if (sdk instanceof JavaSdk && !myProjectJavaSdks.contains(sdk)) {
        final JavaSdk javaSdk = (JavaSdk)sdk;
        if (javaSdk.getVersion() != null && javaSdk.getHomePath() != null) {
          myProjectJavaSdks.add(javaSdk);
        }
      }
    }
  }

  public Set<JavaSdk> getProjectJavaSdks() {
    return myProjectJavaSdks;
  }

  public BuildLoggingManager getLoggingManager() {
    return myLoggingManager;
  }

  public synchronized void incUsageCounter() {
    myUseCounter++;
  }

  public void release() {
    boolean shouldClose;
    synchronized (this) {
      --myUseCounter;
      shouldClose = myUseCounter == 0;
    }
    if (shouldClose) {
      try {
        timestamps.close();
      }
      finally {
        try {
          dataManager.close();
        }
        catch (IOException e) {
          e.printStackTrace(System.err);
        }
      }
    }
  }
}
