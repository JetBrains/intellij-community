package org.jetbrains.jps.artifacts.ant

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.ArtifactBuildTask
import org.jetbrains.jps.artifacts.Artifact
import org.jetbrains.jps.artifacts.Options

class AntPreBuildTask implements ArtifactBuildTask {
  private final Project project;

  AntPreBuildTask(Project project) {
    this.project = project
  }

  def perform(Artifact artifact, String outputFolder) {
    def Options options = artifact.options["ant-preprocessing"];
    if (options == null) return null;

    new CallAntBuildTask(this.project.binding.ant).invokeAnt(options);
    return null;
  }
}
