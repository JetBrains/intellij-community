package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;

/**
 * @author nik
 */
public class InlineArtifactAction extends AnAction {
  private final ArtifactEditorEx myEditor;

  public InlineArtifactAction(ArtifactEditorEx editor) {
    super(ProjectBundle.message("action.name.inline.artifact"));
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    PackagingElement<?> element = selection.getElementIfSingle();
    e.getPresentation().setEnabled(element instanceof ArtifactPackagingElement && node != null && node.getParentElement(element) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getLayoutTreeComponent();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null || !(element instanceof ArtifactPackagingElement)) return;

    CompositePackagingElement<?> parent = node.getParentElement(element);
    if (parent == null) {
      return;
    }
    if (!treeComponent.checkCanRemove(selection.getNodes())) return;
    if (!treeComponent.checkCanAdd(null, parent, node)) return;

    treeComponent.ensureRootIsWritable();
    parent.removeChild(element);
    final PackagingEditorContext context = myEditor.getContext();
    final Artifact artifact = ((ArtifactPackagingElement)element).findArtifact(context);
    if (artifact != null) {
      final CompositePackagingElement<?> rootElement = artifact.getRootElement();
      if (rootElement instanceof ArtifactRootElement<?>) {
        for (PackagingElement<?> child : rootElement.getChildren()) {
          parent.addOrFindChild(ArtifactUtil.copyWithChildren(child, context.getProject()));
        }
      }
      else {
        parent.addOrFindChild(ArtifactUtil.copyWithChildren(rootElement, context.getProject()));
      }
    }
    treeComponent.rebuildTree();
  }
}
