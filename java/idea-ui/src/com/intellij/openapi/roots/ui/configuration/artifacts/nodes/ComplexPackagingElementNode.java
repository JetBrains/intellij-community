package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTree;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.treeStructure.SimpleTree;

import java.awt.event.InputEvent;
import java.util.Collection;

/**
 * @author nik
 */
public class ComplexPackagingElementNode extends PackagingElementNode<ComplexPackagingElement<?>> {
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;

  public ComplexPackagingElementNode(ComplexPackagingElement<?> element, ArtifactEditorContext context, CompositePackagingElementNode parentNode,
                                     CompositePackagingElement<?> parentElement,
                                     ComplexElementSubstitutionParameters substitutionParameters, Collection<PackagingNodeSource> nodeSources) {
    super(element, context, parentNode, parentElement, nodeSources);
    mySubstitutionParameters = substitutionParameters;
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    mySubstitutionParameters.setShowContent(this);
    final LayoutTree layoutTree = (LayoutTree)tree;
    final CompositePackagingElementNode parentNode = getParentNode();
    if (parentNode != null) {
      layoutTree.addSubtreeToUpdate(parentNode);
    }
  }

}
