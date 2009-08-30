package com.intellij.compiler.ant.artifacts;

import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.compiler.ant.taskdefs.Delete;
import com.intellij.compiler.ant.BuildProperties;
import com.intellij.packaging.artifacts.Artifact;

/**
 * @author nik
 */
public class CleanArtifactTarget extends Target {
  public CleanArtifactTarget(Artifact artifact, ArtifactAntGenerationContextImpl context) {
    super(context.getCleanTargetName(artifact), null, "clean " + artifact.getName() + " artifact output", null);
    add(new Delete(BuildProperties.propertyRef(context.getConfiguredArtifactOutputProperty(artifact))));
  }
}
