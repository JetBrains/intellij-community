package com.intellij.packaging.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class ArtifactManager implements ArtifactModel {
  public static final Topic<ArtifactListener> TOPIC = Topic.create("artifacts changes", ArtifactListener.class);

  public static ArtifactManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ArtifactManager.class);
  }

  public abstract Collection<? extends Artifact> getEnabledArtifacts();

  public abstract ModifiableArtifactModel createModifiableModel();

}
