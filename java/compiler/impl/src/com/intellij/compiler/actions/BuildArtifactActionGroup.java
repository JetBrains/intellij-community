package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class BuildArtifactActionGroup extends ActionGroup {
  public BuildArtifactActionGroup() {
    super("Build Artifact", true);
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return EMPTY_ARRAY;

    final Artifact[] artifacts = ArtifactManager.getInstance(project).getArtifacts();
    return ContainerUtil.map2Array(artifacts, AnAction.class, new NotNullFunction<Artifact, AnAction>() {
      @NotNull
      public AnAction fun(Artifact artifact) {
        return new BuildArtifactAction(project, artifact);
      }
    });
  }
}
