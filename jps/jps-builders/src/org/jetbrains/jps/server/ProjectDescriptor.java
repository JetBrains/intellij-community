package org.jetbrains.jps.server;

import org.jetbrains.jps.Project;
import org.jetbrains.jps.incremental.FSState;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;

/**
* @author Eugene Zhuravlev
*         Date: 1/8/12
*/
public class ProjectDescriptor {
  public final String projectName;
  public final Project project;
  public final FSState fsState;
  public final ProjectTimestamps timestamps;

  ProjectDescriptor(String projectName, Project project, FSState fsState, ProjectTimestamps timestamps) {
    this.projectName = projectName;
    this.project = project;
    this.fsState = fsState;
    this.timestamps = timestamps;
  }

  public void close() {
    timestamps.close();
  }
}
