package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingNodeSource;

import java.util.Collection;

/**
 * @author nik
 */
public class HideContentAction extends DumbAwareAction {
  private ArtifactEditorEx myArtifactEditor;

  public HideContentAction(ArtifactEditorEx artifactEditor) {
    super("Hide Content");
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node != null) {
      final Collection<PackagingNodeSource> sources = node.getNodeSources();
      if (!sources.isEmpty()) {
        String description;
        if (sources.size() == 1) {
          description = "Hide Content of '" + sources.iterator().next().getPresentableName() + "'";
        }
        else {
          description = "Hide Content";
        }
        e.getPresentation().setVisible(true);
        e.getPresentation().setText(description);
        return;
      }
    }
    e.getPresentation().setVisible(false);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;

    final Collection<PackagingNodeSource> sources = node.getNodeSources();
    for (PackagingNodeSource source : sources) {
      myArtifactEditor.getSubstitutionParameters().dontSubstitute(source.getSourceElement());
      myArtifactEditor.getLayoutTreeComponent().getLayoutTree().addSubtreeToUpdate(source.getSourceParentNode());
    }
  }
}
