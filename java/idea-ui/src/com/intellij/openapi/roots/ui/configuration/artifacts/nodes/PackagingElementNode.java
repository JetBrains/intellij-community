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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorContextImpl;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class PackagingElementNode<E extends PackagingElement<?>> extends ArtifactsTreeNode {
  private final List<E> myPackagingElements;
  private final Map<PackagingElement<?>, CompositePackagingElement<?>> myParentElements = new HashMap<PackagingElement<?>, CompositePackagingElement<?>>(1);
  private final MultiValuesMap<PackagingElement<?>, PackagingNodeSource> myNodeSources = new MultiValuesMap<PackagingElement<?>, PackagingNodeSource>();
  private final CompositePackagingElementNode myParentNode;

  public PackagingElementNode(@NotNull E packagingElement, ArtifactEditorContext context, @Nullable CompositePackagingElementNode parentNode,
                              @Nullable CompositePackagingElement<?> parentElement,
                              @NotNull Collection<PackagingNodeSource> nodeSources) {
    super(context, parentNode, packagingElement.createPresentation(context));
    myParentNode = parentNode;
    myParentElements.put(packagingElement, parentElement);
    myNodeSources.putAll(packagingElement, nodeSources);
    myPackagingElements = new SmartList<E>();
    doAddElement(packagingElement);
  }

  private void doAddElement(E packagingElement) {
    myPackagingElements.add(packagingElement);
    ((ArtifactEditorContextImpl)myContext).getValidationManager().elementAddedToNode(this, packagingElement);
  }

  @Nullable 
  public CompositePackagingElement<?> getParentElement(PackagingElement<?> element) {
    return myParentElements.get(element);
  }

  @Nullable
  public CompositePackagingElementNode getParentNode() {
    return myParentNode;
  }

  public List<E> getPackagingElements() {
    return myPackagingElements;
  }

  @Nullable
  public E getElementIfSingle() {
    return myPackagingElements.size() == 1 ? myPackagingElements.get(0) : null;
  }

  @Override
  public Object[] getEqualityObjects() {
    return myPackagingElements.toArray(new Object[myPackagingElements.size()]);
  }

  @Override
  protected SimpleNode[] buildChildren() {
    return NO_CHILDREN;
  }

  public E getFirstElement() {
    return myPackagingElements.get(0);
  }

  @Override
  protected void update(PresentationData presentation) {
    final String message = ((ArtifactEditorContextImpl)myContext).getValidationManager().getProblem(this);
    if (message == null) {
      super.update(presentation);
      return;
    }

    getElementPresentation().render(presentation, addErrorHighlighting(SimpleTextAttributes.REGULAR_ATTRIBUTES), 
                                    addErrorHighlighting(SimpleTextAttributes.GRAY_ATTRIBUTES));
    presentation.setTooltip(message);
  }

  private SimpleTextAttributes addErrorHighlighting(SimpleTextAttributes attributes) {
    final TextAttributes textAttributes = attributes.toTextAttributes();
    textAttributes.setEffectType(EffectType.WAVE_UNDERSCORE);
    textAttributes.setEffectColor(Color.RED);
    return SimpleTextAttributes.fromTextAttributes(textAttributes);
  }

  void addElement(PackagingElement<?> element, CompositePackagingElement parentElement, Collection<PackagingNodeSource> nodeSource) {
    doAddElement((E)element);
    myParentElements.put(element, parentElement);
    myNodeSources.putAll(element, nodeSource);
  }

  @NotNull
  public Collection<PackagingNodeSource> getNodeSources() {
    return myNodeSources.values();
  }

  @NotNull
  public Collection<PackagingNodeSource> getNodeSource(@NotNull PackagingElement<?> element) {
    final Collection<PackagingNodeSource> nodeSources = myNodeSources.get(element);
    return nodeSources != null ? nodeSources : Collections.<PackagingNodeSource>emptyList();
  }

  public ArtifactEditorContext getContext() {
    return myContext;
  }

  @Nullable
  public CompositePackagingElementNode findCompositeChild(@NotNull String name) {
    final SimpleNode[] children = getChildren();
    for (SimpleNode child : children) {
      if (child instanceof CompositePackagingElementNode) {
        final CompositePackagingElementNode composite = (CompositePackagingElementNode)child;
        if (name.equals(composite.getFirstElement().getName())) {
          return composite;
        }
      }
    }
    return null;
  }
}
