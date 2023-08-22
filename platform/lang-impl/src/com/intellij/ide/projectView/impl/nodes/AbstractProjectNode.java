// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class AbstractProjectNode extends ProjectViewNode<Project> {
  protected AbstractProjectNode(Project project, @NotNull Project value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  protected Collection<AbstractTreeNode<?>> modulesAndGroups(@NotNull Collection<? extends ModuleDescription> modules) {
    if (getSettings().isFlattenModules()) {
      return ContainerUtil.mapNotNull(modules, moduleDescription -> {
        try {
          return createModuleNode(moduleDescription);
        }
        catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
          LOG.error(e);
          return null;
        }
      });
    }

    Set<String> topLevelGroups = new LinkedHashSet<>();
    Set<ModuleDescription> nonGroupedModules = new LinkedHashSet<>(modules);
    List<String> commonGroupsPath = null;
    for (final ModuleDescription moduleDescription : modules) {
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

    List<AbstractTreeNode<?>> result = new ArrayList<>();
    try {
      if (modules.size() > 1) {
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
        ContainerUtil.addIfNotNull(result, createModuleNode(ContainerUtil.getFirstItem(modules)));
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

  @NotNull
  protected abstract AbstractTreeNode<?> createModuleGroup(@NotNull Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

  @Nullable
  private AbstractTreeNode<?> createModuleNode(final ModuleDescription moduleDescription)
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

  @NotNull
  protected abstract AbstractTreeNode<?> createModuleGroupNode(@NotNull ModuleGroup moduleGroup)
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
