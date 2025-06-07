// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public final class PackagingElementPath {
  public static final PackagingElementPath EMPTY = new PackagingElementPath(null, null);
  private final PackagingElementPath myParentPath;
  private final PackagingElement<?> myLastElement;

  private PackagingElementPath(PackagingElementPath parentPath, PackagingElement<?> lastElement) {
    myParentPath = parentPath;
    myLastElement = lastElement;
  }

  public PackagingElementPath appendComplex(ComplexPackagingElement<?> element) {
    return new PackagingElementPath(this, element);
  }

  public PackagingElementPath appendComposite(CompositePackagingElement<?> element) {
    return new PackagingElementPath(this, element);
  }

  public @NotNull String getPathString() {
    return getPathString("/");
  }

  public @NotNull String getPathString(String separator) {
    return getPathStringFrom(separator, null);
  }

  public @NotNull String getPathStringFrom(String separator, @Nullable CompositePackagingElement<?> ancestor) {
    final List<CompositePackagingElement<?>> parents = getParentsFrom(ancestor);
    // StringUtil.join ignores empty strings whereas this monstrosity doesn't
    return ContainerUtil.reverse(parents).stream().map(RenameablePackagingElement::getName).collect(Collectors.joining("/"));
  }

  public List<CompositePackagingElement<?>> getParents() {
    return getParentsFrom(null);
  }

  public List<CompositePackagingElement<?>> getParentsFrom(@Nullable CompositePackagingElement<?> ancestor) {
    List<CompositePackagingElement<?>> result = new SmartList<>();
    PackagingElementPath path = this;
    while (path != EMPTY && path.myLastElement != ancestor) {
      if (path.myLastElement instanceof CompositePackagingElement<?>) {
        result.add((CompositePackagingElement)path.myLastElement);
      }
      path = path.myParentPath;
    }
    return result;
  }

  public List<PackagingElement<?>> getAllElements() {
    List<PackagingElement<?>> result = new SmartList<>();
    PackagingElementPath path = this;
    while (path != EMPTY) {
      result.add(path.myLastElement);
      path = path.myParentPath;
    }
    return result;
  }

  public @Nullable CompositePackagingElement<?> getLastParent() {
    PackagingElementPath path = this;
    while (path != EMPTY) {
      if (path.myLastElement instanceof CompositePackagingElement<?>) {
        return (CompositePackagingElement)path.myLastElement;
      }
      path = path.myParentPath;
    }
    return null;
  }

  public @Nullable Artifact findLastArtifact(PackagingElementResolvingContext context) {
    PackagingElementPath path = this;
    while (path != EMPTY) {
      final PackagingElement<?> element = path.myLastElement;
      if (element instanceof ArtifactPackagingElement) {
        return ((ArtifactPackagingElement)element).findArtifact(context);
      }
      path = path.myParentPath;
    }
    return null;
  }

  public static PackagingElementPath createPath(@NotNull List<? extends PackagingElement<?>> elements) {
    PackagingElementPath path = EMPTY;
    for (PackagingElement<?> element : elements) {
      path = new PackagingElementPath(path, element);
    }
    return path;
  }
}
