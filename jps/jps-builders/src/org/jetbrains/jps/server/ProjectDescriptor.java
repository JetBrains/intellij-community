package org.jetbrains.jps.server;

import org.jetbrains.jps.Project;
import org.jetbrains.jps.incremental.FSState;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;

/**
* @author Eugene Zhuravlev
*         Date: 1/8/12
*/
public final class ProjectDescriptor {
  public final String projectName;
  public final Project project;
  public final FSState fsState;
  public final ProjectTimestamps timestamps;
  public final BuildDataManager dataManager;
  public ModuleRootsIndex rootsIndex;

  private int myUseCounter = 1;

  ProjectDescriptor(String projectName, Project project, FSState fsState, ProjectTimestamps timestamps, BuildDataManager dataManager) {
    this.projectName = projectName;
    this.project = project;
    this.fsState = fsState;
    this.timestamps = timestamps;
    this.dataManager = dataManager;
    this.rootsIndex = new ModuleRootsIndex(project);
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
        dataManager.close();
      }
    }
  }
}
