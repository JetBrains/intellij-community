package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.util.Icons;

/**
 * @author nik
 */
public class RemovePackagingElementAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;

  public RemovePackagingElementAction(ArtifactEditorEx artifactEditor) {
    super(ProjectBundle.message("action.name.remove.packaging.element"), ProjectBundle.message("action.description.remove.packaging.elements"), Icons.DELETE_ICON);
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!myArtifactEditor.getLayoutTreeComponent().getSelection().getElements().isEmpty()
                                   && !myArtifactEditor.getLayoutTreeComponent().isEditing());
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.removeSelectedElements();
  }
}
