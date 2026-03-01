// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.NodeSortOrder;
import com.intellij.ide.projectView.NodeSortSettings;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.LoadedModuleDescription;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleDescription;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

public abstract class AbstractProjectNode extends ProjectViewNode<Project> {
  protected AbstractProjectNode(Project project, @NotNull Project value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected @Unmodifiable @NotNull Collection<AbstractTreeNode<?>> modulesAndGroups(@NotNull Collection<? extends ModuleDescription> modulesWithTopLevelContentRoots) {
    if (getSettings().isFlattenModules()) {
      return ContainerUtil.mapNotNull(modulesWithTopLevelContentRoots, moduleDescription -> {
        try {
          return createModuleNode(moduleDescription);
        }
        catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
          LOG.error(e);
          return null;
        }
      });
    }

    List<AbstractTreeNode<?>> result = new ArrayList<>();
    try {
      if (modulesWithTopLevelContentRoots.size() > 1) {
        Set<String> topLevelGroups = new LinkedHashSet<>();
        Set<ModuleDescription> nonGroupedModules = new LinkedHashSet<>(modulesWithTopLevelContentRoots);
        List<String> commonGroupsPath = null;
        for (final ModuleDescription moduleDescription : modulesWithTopLevelContentRoots) {
          final List<String> path = ModuleGrouper.instanceFor(myProject).getGroupPath(moduleDescription);
          if (!path.isEmpty()) {
            final String topLevelGroupName = path.get(0);
            topLevelGroups.add(topLevelGroupName);
            nonGroupedModules.remove(moduleDescription);
            if (commonGroupsPath == null) {
              commonGroupsPath = path;
            }
            else {
              int commonPartLen = Math.min(commonGroupsPath.size(), path.size());
              OptionalLong firstDifference = StreamEx.zip(commonGroupsPath.subList(0, commonPartLen), path.subList(0, commonPartLen), String::equals).indexOf(false);
              if (firstDifference.isPresent()) {
                commonGroupsPath = commonGroupsPath.subList(0, (int)firstDifference.getAsLong());
              }
              else if (commonPartLen < commonGroupsPath.size()) {
                commonGroupsPath = commonGroupsPath.subList(0, commonPartLen);
              }
            }
          }
        }

        if (commonGroupsPath != null && !commonGroupsPath.isEmpty()) {
          result.add(createModuleGroupNode(new ModuleGroup(commonGroupsPath)));
        }
        else {
          for (String groupPath : topLevelGroups) {
            result.add(createModuleGroupNode(new ModuleGroup(Collections.singletonList(groupPath))));
          }
        }
        for (ModuleDescription moduleDescription : nonGroupedModules) {
          ContainerUtil.addIfNotNull(result, createModuleNode(moduleDescription));
        }
      }
      else {
        ContainerUtil.addIfNotNull(result, createModuleNode(ContainerUtil.getFirstItem(modulesWithTopLevelContentRoots)));
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
      return new ArrayList<>();
    }
    return result;
  }

  protected abstract @NotNull AbstractTreeNode<?> createModuleGroup(@NotNull Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

  private @Nullable AbstractTreeNode<?> createModuleNode(final ModuleDescription moduleDescription)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    if (moduleDescription instanceof LoadedModuleDescription) {
      return createModuleGroup(((LoadedModuleDescription)moduleDescription).getModule());
    }
    if (moduleDescription instanceof UnloadedModuleDescription) {
      return createUnloadedModuleNode((UnloadedModuleDescription)moduleDescription);
    }
    return null;
  }

  protected AbstractTreeNode<?> createUnloadedModuleNode(UnloadedModuleDescription moduleDescription) {
    return null;
  }

  protected abstract @NotNull AbstractTreeNode<?> createModuleGroupNode(@NotNull ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

  @Override
  public void update(@NotNull PresentationData presentation) {
    presentation.setIcon(PlatformIcons.PROJECT_ICON);
    presentation.setPresentableText(getProject().getName());
  }

  @Override
  public String getTestPresentation() {
    return "Project";
  }

  @Override
  public boolean contains(@NotNull VirtualFile vFile) {
    assert myProject != null;
    return ProjectViewPane.canBeSelectedInProjectView(myProject, vFile);
  }

  @Override
  public @NotNull NodeSortOrder getSortOrder(@NotNull NodeSortSettings settings) {
    return NodeSortOrder.PROJECT_ROOT;
  }
}
