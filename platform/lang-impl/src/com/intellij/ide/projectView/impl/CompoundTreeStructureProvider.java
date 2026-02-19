// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * This class is intended to combine all providers for batch usages.
 */
public final class CompoundTreeStructureProvider implements TreeStructureProvider {
  private static final Key<TreeStructureProvider> KEY = Key.create("TreeStructureProvider");
  private static final Logger LOG = Logger.getInstance(CompoundTreeStructureProvider.class);
  private final Project myProject;

  /**
   * @return a shared instance for the specified project
   */
  public static @Nullable TreeStructureProvider get(@Nullable Project project) {
    if (project == null || project.isDisposed() || project.isDefault()) return null;
    TreeStructureProvider provider = project.getUserData(KEY);
    if (provider != null) return provider;
    provider = new CompoundTreeStructureProvider(project);
    project.putUserData(KEY, provider);
    return provider;
  }

  private CompoundTreeStructureProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                                         @NotNull Collection<AbstractTreeNode<?>> children,
                                                         ViewSettings settings) {
    if (myProject.isDisposed()) return children;
    for (TreeStructureProvider provider : EP.getExtensions(myProject)) {
      try {
        children = provider.modify(parent, children, settings);
        if (ContainerUtil.exists(children, Objects::isNull)) {
          LOG.warn("null child provided by " + provider);
          children = StreamEx.of(children).nonNull().toImmutableList();
        }
      }
      catch (IndexNotReadyException exception) {
        throw new ProcessCanceledException(exception);
      }
      catch (ProcessCanceledException exception) {
        throw exception;
      }
      catch (Exception exception) {
        LOG.warn("unexpected error in " + provider, exception);
      }
    }
    children.forEach(node -> node.setParent(parent));
    return children;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull Collection<? extends AbstractTreeNode<?>> selection) {
    if (myProject.isDisposed() || selection.isEmpty()) return;
    for (TreeStructureProvider provider : ContainerUtil.reverse(EP.getExtensions(myProject))) {
      try {
        provider.uiDataSnapshot(sink, selection);
      }
      catch (IndexNotReadyException | ProcessCanceledException ignore) {
      }
      catch (Exception exception) {
        LOG.warn("unexpected error in " + provider, exception);
      }
    }
  }
}
