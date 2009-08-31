package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.CachingSimpleNode;

/**
 * @author nik
 */
public abstract class ArtifactsTreeNode extends CachingSimpleNode {
  private final TreeNodePresentation myPresentation;
  protected final ArtifactEditorContext myContext;

  protected ArtifactsTreeNode(ArtifactEditorContext context, NodeDescriptor parentDescriptor, final TreeNodePresentation presentation) {
    super(context.getProject(), parentDescriptor);
    myContext = context;
    myPresentation = presentation;
  }

  @Override
  protected void update(PresentationData presentation) {
    myPresentation.render(presentation, SimpleTextAttributes.REGULAR_ATTRIBUTES, SimpleTextAttributes.GRAY_ATTRIBUTES);
    presentation.setTooltip(myPresentation.getTooltipText());
  }

  public TreeNodePresentation getElementPresentation() {
    return myPresentation;
  }

  @Override
  public int getWeight() {
    return myPresentation.getWeight();
  }

  @Override
  public String getName() {
    return myPresentation.getPresentableName();
  }
}
