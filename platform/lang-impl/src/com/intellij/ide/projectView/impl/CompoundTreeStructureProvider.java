// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public class CompoundTreeStructureProvider implements TreeStructureProvider {
  private static final Logger LOG = Logger.getInstance(CompoundTreeStructureProvider.class);
  private final Project project;

  public CompoundTreeStructureProvider(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    if (!children.isEmpty()) {
      TreeStructureProvider[] providers = getProviders();
      if (providers != null && providers.length != 0) {
        for (TreeStructureProvider provider : providers) {
          try {
            children = provider.modify(parent, children, settings);
            if (children.stream().anyMatch(Objects::isNull)) {
              LOG.warn("null child provided by " + provider);
              children = StreamEx.of(children).filter(Objects::nonNull).toImmutableList();
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
      }
      children.forEach(node -> node.setParent(parent));
    }
    return children;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selection, String id) {
    if (id != null && selection != null && !selection.isEmpty()) {
      TreeStructureProvider[] providers = getProviders();
      if (providers != null && providers.length != 0) {
        for (TreeStructureProvider provider : providers) {
          if (isDumbMode(provider)) continue;
          Object data = provider.getData(selection, id);
          if (data != null) return data;
        }
      }
    }
    return null;
  }

  @Nullable
  private TreeStructureProvider[] getProviders() {
    return project.isDisposed() ? null : EP_NAME.getExtensions(project);
  }

  private boolean isDumbMode(TreeStructureProvider provider) {
    return project.isDisposed() || DumbService.isDumb(project) && !DumbService.isDumbAware(provider);
  }
}
