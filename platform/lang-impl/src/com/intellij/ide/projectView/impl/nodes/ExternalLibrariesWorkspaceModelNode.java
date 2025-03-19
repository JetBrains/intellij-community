// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ExternalLibrariesWorkspaceModelNode extends ProjectViewNode<Unit> {
  private final @NotNull Collection<VirtualFile> myRoots;
  private final @NotNull Collection<VirtualFile> myExcludedRoots;
  private final @NotNull ItemPresentation myItemPresentation;

  public ExternalLibrariesWorkspaceModelNode(@NotNull Project project,
                                             @NotNull Collection<VirtualFile> roots,
                                             @NotNull String libraryName,
                                             ViewSettings settings) {
    this(project, roots, Collections.emptyList(), libraryName, null, settings);
  }

  public ExternalLibrariesWorkspaceModelNode(@NotNull Project project,
                                             @NotNull Collection<VirtualFile> roots,
                                             @NotNull Collection<VirtualFile> excludedRoots,
                                             @NotNull String libraryName,
                                             @Nullable Icon icon,
                                             ViewSettings settings) {
    this(project, roots, excludedRoots, new Presentation(libraryName, icon), settings);
  }

  private record Presentation(@NotNull String libraryName, @Nullable Icon icon) implements ItemPresentation {
    @Override
    public String getPresentableText() {
      return libraryName;
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
      return icon;
    }
  }

  public ExternalLibrariesWorkspaceModelNode(@NotNull Project project,
                                             @NotNull Collection<VirtualFile> roots,
                                             @NotNull Collection<VirtualFile> excludedRoots,
                                             @NotNull ItemPresentation itemPresentation,
                                             ViewSettings settings) {
    super(project, Unit.INSTANCE, settings);
    myRoots = List.copyOf(roots);
    myExcludedRoots = List.copyOf(excludedRoots);
    myItemPresentation = itemPresentation;
  }

  @Override
  public @NotNull Collection<VirtualFile> getRoots() {
    return myRoots;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return VfsUtilCore.isUnderFiles(file, myRoots) && !VfsUtilCore.isUnderFiles(file, myExcludedRoots);
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    Project project = Objects.requireNonNull(getProject());
    List<VirtualFile> children = ContainerUtil.filter(myRoots, file -> file.isValid() && !myExcludedRoots.contains(file));
    return ProjectViewDirectoryHelper.getInstance(project).createFileAndDirectoryNodes(children, getSettings());
  }

  @Override
  public String getName() {
    return myItemPresentation.getPresentableText();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.updateFrom(myItemPresentation);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExternalLibrariesWorkspaceModelNode node = (ExternalLibrariesWorkspaceModelNode)o;
    return Objects.equals(myRoots, node.myRoots) &&
           Objects.equals(myExcludedRoots, node.myExcludedRoots) &&
           Objects.equals(myItemPresentation, node.myItemPresentation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myRoots, myExcludedRoots, myItemPresentation);
  }
}
