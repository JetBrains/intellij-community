/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactProblemDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemType;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class PackagingElementNode<E extends PackagingElement<?>> extends ArtifactsTreeNode {
  private final List<E> myPackagingElements;
  private final Map<PackagingElement<?>, CompositePackagingElement<?>> myParentElements = new HashMap<>(1);
  private final MultiValuesMap<PackagingElement<?>, PackagingNodeSource> myNodeSources = new MultiValuesMap<>();
  private final CompositePackagingElementNode myParentNode;

  public PackagingElementNode(@NotNull E packagingElement, ArtifactEditorContext context, @Nullable CompositePackagingElementNode parentNode,
                              @Nullable CompositePackagingElement<?> parentElement,
                              @NotNull Collection<PackagingNodeSource> nodeSources) {
    super(context, parentNode, packagingElement.createPresentation(context));
    myParentNode = parentNode;
    myParentElements.put(packagingElement, parentElement);
    myNodeSources.putAll(packagingElement, nodeSources);
    myPackagingElements = new SmartList<>();
    doAddElement(packagingElement);
  }

  private void doAddElement(E packagingElement) {
    myPackagingElements.add(packagingElement);
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

  @NotNull
  @Override
  public Object[] getEqualityObjects() {
    return ArrayUtil.toObjectArray(myPackagingElements);
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
    final Collection<ArtifactProblemDescription> problems = ((ArtifactEditorImpl)myContext.getThisArtifactEditor()).getValidationManager().getProblems(this);
    if (problems == null || problems.isEmpty()) {
      super.update(presentation);
      return;
    }
    StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    final String tooltip;
    boolean isError = false;
    try {
      for (ArtifactProblemDescription problem : problems) {
        isError |= problem.getSeverity() == ProjectStructureProblemType.Severity.ERROR;
        buffer.append(problem.getMessage(false)).append("<br>");
      }
      tooltip = XmlStringUtil.wrapInHtml(buffer);
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }

    getElementPresentation().render(presentation, addErrorHighlighting(isError, SimpleTextAttributes.REGULAR_ATTRIBUTES),
                                    addErrorHighlighting(isError, SimpleTextAttributes.GRAY_ATTRIBUTES));
    presentation.setTooltip(tooltip);
  }

  private static SimpleTextAttributes addErrorHighlighting(boolean error, SimpleTextAttributes attributes) {
    final TextAttributes textAttributes = attributes.toTextAttributes();
    textAttributes.setEffectType(EffectType.WAVE_UNDERSCORE);
    textAttributes.setEffectColor(error ? JBColor.RED : JBColor.GRAY);
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


  public List<PackagingElementNode<?>> getNodesByPath(List<PackagingElement<?>> pathToPlace) {
    List<PackagingElementNode<?>> result = new ArrayList<>();
    PackagingElementNode<?> current = this;
    int i = 0;
    result.add(current);
    while (current != null && i < pathToPlace.size()) {
      final SimpleNode[] children = current.getCached();
      if (children == null) {
        break;
      }

      PackagingElementNode<?> next = null;
      final PackagingElement<?> element = pathToPlace.get(i);

      search:
      for (SimpleNode child : children) {
        if (child instanceof PackagingElementNode<?>) {
          PackagingElementNode<?> childNode = (PackagingElementNode<?>)child;
          for (PackagingElement<?> childElement : childNode.getPackagingElements()) {
            if (childElement.isEqualTo(element)) {
              next = childNode;
              break search;
            }
          }
          for (PackagingNodeSource nodeSource : childNode.getNodeSources()) {
            if (nodeSource.getSourceElement().isEqualTo(element)) {
              next = current;
              break search;
            }
          }
        }
      }
      current = next;
      if (current != null) {
        result.add(current);
      }
      i++;
    }
    return result;
  }
}
