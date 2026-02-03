// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PackagingNodeSource {
  private final ComplexPackagingElement<?> mySourceElement;
  private final PackagingElementNode<?> mySourceParentNode;
  private final CompositePackagingElement<?> mySourceParentElement;
  private final Collection<PackagingNodeSource> myParentSources;

  public PackagingNodeSource(@NotNull ComplexPackagingElement<?> sourceElement,
                             @NotNull PackagingElementNode<?> sourceParentNode,
                             @NotNull CompositePackagingElement<?> sourceParentElement,
                             @NotNull Collection<PackagingNodeSource> parentSources) {
    mySourceElement = sourceElement;
    mySourceParentNode = sourceParentNode;
    mySourceParentElement = sourceParentElement;
    myParentSources = parentSources;
  }

  public @NotNull ComplexPackagingElement<?> getSourceElement() {
    return mySourceElement;
  }

  public @NotNull PackagingElementNode<?> getSourceParentNode() {
    return mySourceParentNode;
  }

  public @NotNull CompositePackagingElement<?> getSourceParentElement() {
    return mySourceParentElement;
  }

  public @NotNull Collection<PackagingNodeSource> getParentSources() {
    return myParentSources;
  }

  public @NlsSafe String getPresentableName() {
    return mySourceElement.createPresentation(mySourceParentNode.getContext()).getPresentableName();
  }
}
