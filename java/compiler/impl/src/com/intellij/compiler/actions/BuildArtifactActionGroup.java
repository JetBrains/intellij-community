package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    final Artifact[] artifacts = ArtifactManager.getInstance(project).getSortedArtifacts();
    List<AnAction> actions = new ArrayList<AnAction>();
    for (Artifact artifact : artifacts) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        actions.add(new BuildArtifactAction(project, artifact));
      }
    }
    return actions.toArray(new AnAction[actions.size()]);
  }
}
