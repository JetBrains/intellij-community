package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.packaging.artifacts.Artifact;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class ChooseArtifactsDialog extends ChooseElementsDialog<Artifact> {

  public ChooseArtifactsDialog(Project project, List<? extends Artifact> items, String title, String description) {
    super(project, items, title, description);
  }

  public ChooseArtifactsDialog(JComponent component, String title, List<Artifact> items) {
    super(component, items, title, true);
  }

  protected String getItemText(Artifact item) {
    return item.getName();
  }

  protected Icon getItemIcon(Artifact item) {
    return item.getArtifactType().getIcon();
  }
}