package org.jetbrains.jps.artifacts

/**
 * @author nik
 */
public interface ArtifactBuildTask {
  def perform(Artifact artifact, String outputFolder)
}