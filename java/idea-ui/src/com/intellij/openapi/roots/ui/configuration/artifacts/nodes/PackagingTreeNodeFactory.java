// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class PackagingTreeNodeFactory {
  private PackagingTreeNodeFactory() {
  }

  public static void addNodes(@NotNull List<? extends PackagingElement<?>> elements, @NotNull CompositePackagingElementNode parentNode,
                              @NotNull CompositePackagingElement parentElement, @NotNull ArtifactEditorContext context,
                              @NotNull ComplexElementSubstitutionParameters substitutionParameters, @NotNull Collection<PackagingNodeSource> nodeSources,
                              @NotNull List<PackagingElementNode<?>> nodes,
                              ArtifactType artifactType,
                              Set<? super PackagingElement<?>> processed) {
    for (PackagingElement<?> element : elements) {
      final PackagingElementNode<?> prev = findEqual(nodes, element);
      if (prev != null) {
        prev.addElement(element, parentElement, nodeSources);
        continue;
      }

      if (element instanceof ArtifactRootElement) {
        throw new AssertionError("artifact root not expected here");
      }
      else if (element instanceof CompositePackagingElement) {
        nodes.add(new CompositePackagingElementNode((CompositePackagingElement<?>)element, context, parentNode, parentElement, substitutionParameters, nodeSources,
                                                    artifactType));
      }
      else if (element instanceof ComplexPackagingElement<?> complexElement) {
        if (processed.add(element) && substitutionParameters.shouldSubstitute(complexElement)) {
          final List<? extends PackagingElement<?>> substitution = complexElement.getSubstitution(context, artifactType);
          if (substitution != null) {
            final PackagingNodeSource source = new PackagingNodeSource(complexElement, parentNode, parentElement, nodeSources);
            addNodes(substitution, parentNode, parentElement, context, substitutionParameters, Collections.singletonList(source), nodes,
                     artifactType, processed);
            continue;
          }
        }
        nodes.add(new ComplexPackagingElementNode(complexElement, context, parentNode, parentElement, substitutionParameters, nodeSources));
      }
      else {
        nodes.add(new PackagingElementNode<PackagingElement<?>>(element, context, parentNode, parentElement, nodeSources));
      }
    }
  }

  @Nullable
  private static PackagingElementNode<?> findEqual(List<? extends PackagingElementNode<?>> children, PackagingElement<?> element) {
    for (PackagingElementNode<?> node : children) {
      if (node.getFirstElement().isEqualTo(element)) {
        return node;
      }
    }
    return null;
  }

  @NotNull
  public static ArtifactRootNode createRootNode(CompositePackagingElement<?> rootElement, ArtifactEditorContext context,
                                                ComplexElementSubstitutionParameters substitutionParameters,
                                                final ArtifactType artifactType) {
    return new ArtifactRootNode(rootElement, context, substitutionParameters, artifactType);
  }
}
