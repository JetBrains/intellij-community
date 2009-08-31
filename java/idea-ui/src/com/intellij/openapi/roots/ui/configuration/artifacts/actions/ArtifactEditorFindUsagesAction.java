package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;

import java.awt.*;

/**
* @author nik
*/
public class ArtifactEditorFindUsagesAction extends FindUsagesInProjectStructureActionBase {
  private LayoutTreeComponent myLayoutTreeComponent;

  public ArtifactEditorFindUsagesAction(LayoutTreeComponent layoutTreeComponent, Project project) {
    super(layoutTreeComponent.getLayoutTree(), project);
    myLayoutTreeComponent = layoutTreeComponent;
  }

  protected boolean isEnabled() {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    return node != null && node.getElementPresentation().getSourceObject() != null;
  }

  protected Object getSelectedObject() {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    return node != null ? node.getElementPresentation().getSourceObject() : null;
  }

  protected RelativePoint getPointToShowResults() {
    final int selectedRow = myLayoutTreeComponent.getLayoutTree().getSelectionRows()[0];
    final Rectangle rowBounds = myLayoutTreeComponent.getLayoutTree().getRowBounds(selectedRow);
    final Point location = rowBounds.getLocation();
    location.y += rowBounds.height;
    return new RelativePoint(myLayoutTreeComponent.getLayoutTree(), location);
  }
}
