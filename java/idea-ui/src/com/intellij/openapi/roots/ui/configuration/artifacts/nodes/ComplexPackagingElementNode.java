/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTree;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.treeStructure.SimpleTree;

import java.awt.event.InputEvent;
import java.util.Collection;

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
