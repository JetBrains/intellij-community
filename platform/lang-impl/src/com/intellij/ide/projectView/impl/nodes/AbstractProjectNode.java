/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class AbstractProjectNode extends ProjectViewNode<Project> {
  protected AbstractProjectNode(Project project, Project value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected Collection<AbstractTreeNode> modulesAndGroups(Module[] modules) {
    Map<String, List<Module>> groups = new THashMap<>();
    List<Module> nonGroupedModules = new ArrayList<>(Arrays.asList(modules));
    for (final Module module : modules) {
      final String[] path = ModuleManager.getInstance(getProject()).getModuleGroupPath(module);
      if (path != null) {
        final String topLevelGroupName = path[0];
        groups.computeIfAbsent(topLevelGroupName, k -> new ArrayList<>()).add(module);
        nonGroupedModules.remove(module);
      }
    }
    List<AbstractTreeNode> result = new ArrayList<>();
    try {
      for (String groupPath : groups.keySet()) {
        result.add(createModuleGroupNode(new ModuleGroup(Collections.singletonList(groupPath))));
      }
      for (Module module : nonGroupedModules) {
        result.add(createModuleGroup(module));
      }
    }
    catch (Exception e) {
      LOG.error(e);
      return new ArrayList<>();
    }
    return result;
  }

  protected abstract AbstractTreeNode createModuleGroup(final Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

  protected abstract AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

  @Override
  public void update(PresentationData presentation) {
    presentation.setIcon(PlatformIcons.PROJECT_ICON);
    presentation.setPresentableText(getProject().getName());
  }

  @Override
  public String getTestPresentation() {
    return "Project";
  }

  @Override
  public boolean contains(@NotNull VirtualFile vFile) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    return index.getContentRootForFile(vFile, false) != null ||
           index.isInLibraryClasses(vFile) ||
           index.isInLibrarySource(vFile) ||
           Comparing.equal(vFile.getParent(), myProject.getBaseDir()) ||
           Registry.is("ide.scratch.in.project.view") && vFile.getFileType() == ScratchFileType.INSTANCE;
  }
}
