package com.intellij.packaging.artifacts;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ArtifactManager implements ArtifactModel {
  public static final Topic<ArtifactListener> TOPIC = Topic.create("artifacts changes", ArtifactListener.class);

  public static ArtifactManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ArtifactManager.class);
  }

  public abstract ModifiableArtifactModel createModifiableModel();

  public abstract PackagingElementResolvingContext getResolvingContext();

  public static boolean useArtifactsForDeployment() {
    return Boolean.parseBoolean(System.getProperty("idea.use.artifacts.for.deployment"));
  }
}
