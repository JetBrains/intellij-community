package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;

/**
* @author nik
*/
public class ArtifactEditorNavigateAction extends DumbAwareAction {
  private LayoutTreeComponent myLayoutTreeComponent;

  public ArtifactEditorNavigateAction(LayoutTreeComponent layoutTreeComponent) {
    super(ProjectBundle.message("action.name.facet.navigate"));
    registerCustomShortcutSet(CommonShortcuts.getEditSource(), layoutTreeComponent.getLayoutTree());
    myLayoutTreeComponent = layoutTreeComponent;
  }

  public void update(final AnActionEvent e) {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    e.getPresentation().setEnabled(node != null && node.getElementPresentation().canNavigateToSource());
  }

  public void actionPerformed(final AnActionEvent e) {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    if (node != null) {
      node.getElementPresentation().navigateToSource();
    }
  }
}
