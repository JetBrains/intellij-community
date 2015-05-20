/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.view;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.Navigatable;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 10/10/2014
 */
public class ExternalSystemViewDefaultContributor extends ExternalSystemViewContributor {

  private static final Key<?>[] KEYS = new Key[]{
    ProjectKeys.MODULE,
    ProjectKeys.MODULE_DEPENDENCY,
    ProjectKeys.LIBRARY_DEPENDENCY,
    ProjectKeys.TASK
  };


  @NotNull
  @Override
  public List<Key<?>> getKeys() {
    return Arrays.asList(KEYS);
  }

  @Override
  @NotNull
  public List<ExternalSystemNode<?>> createNodes(final ExternalProjectsView externalProjectsView,
                                                 final MultiMap<Key<?>, DataNode<?>> dataNodes) {
    final List<ExternalSystemNode<?>> result = new SmartList<ExternalSystemNode<?>>();

    addModuleNodes(externalProjectsView, dataNodes, result);
    // add tasks
    result.add(new TasksNode(externalProjectsView, dataNodes.get(ProjectKeys.TASK)));
    addDependenciesNode(externalProjectsView, dataNodes, result);

    return result;
  }

  private static void addDependenciesNode(@NotNull ExternalProjectsView externalProjectsView,
                                          @NotNull MultiMap<Key<?>, DataNode<?>> dataNodes,
                                          @NotNull List<ExternalSystemNode<?>> result) {
    final Collection<DataNode<?>> moduleDeps = dataNodes.get(ProjectKeys.MODULE_DEPENDENCY);
    final Collection<DataNode<?>> libDeps = dataNodes.get(ProjectKeys.LIBRARY_DEPENDENCY);

    if (!moduleDeps.isEmpty() || !libDeps.isEmpty()) {
      final ExternalSystemNode depNode = new MyDependenciesNode(externalProjectsView);

      for (DataNode<?> dataNode : moduleDeps) {
        if (!(dataNode.getData() instanceof ModuleDependencyData)) continue;
        //noinspection unchecked
        depNode.add(new ModuleDependencyDataExternalSystemNode(externalProjectsView, (DataNode<ModuleDependencyData>)dataNode));
      }

      for (DataNode<?> dataNode : libDeps) {
        if (!(dataNode.getData() instanceof LibraryDependencyData)) continue;
        //noinspection unchecked
        final ExternalSystemNode<LibraryDependencyData> libraryDependencyDataExternalSystemNode =
          new LibraryDependencyDataExternalSystemNode(externalProjectsView, (DataNode<LibraryDependencyData>)dataNode);

        depNode.add(libraryDependencyDataExternalSystemNode);
        libraryDependencyDataExternalSystemNode.setErrorLevel(((LibraryDependencyData)dataNode.getData()).getTarget().isUnresolved()
                                                              ? ExternalProjectsStructure.ErrorLevel.ERROR
                                                              : ExternalProjectsStructure.ErrorLevel.NONE);
      }

      result.add(depNode);
    }
  }

  private static void addModuleNodes(@NotNull ExternalProjectsView externalProjectsView,
                                     @NotNull MultiMap<Key<?>, DataNode<?>> dataNodes,
                                     @NotNull List<ExternalSystemNode<?>> result) {
    final Collection<DataNode<?>> moduleDataNodes = dataNodes.get(ProjectKeys.MODULE);
    if (!moduleDataNodes.isEmpty()) {
      final AbstractExternalSystemSettings systemSettings =
        ExternalSystemApiUtil.getSettings(externalProjectsView.getProject(), externalProjectsView.getSystemId());

      for (DataNode<?> dataNode : moduleDataNodes) {
        final ModuleData data = (ModuleData)dataNode.getData();

        final ExternalProjectSettings projectSettings = systemSettings.getLinkedProjectSettings(data.getLinkedExternalProjectPath());
        final boolean isRoot =
          projectSettings != null && data.getLinkedExternalProjectPath().equals(projectSettings.getExternalProjectPath());
        //noinspection unchecked
        final ModuleNode moduleNode = new ModuleNode(externalProjectsView, (DataNode<ModuleData>)dataNode, isRoot);
        result.add(moduleNode);
      }
    }
  }

  @Order(2)
  private static class MyDependenciesNode extends ExternalSystemNode {
    public MyDependenciesNode(ExternalProjectsView externalProjectsView) {
      //noinspection unchecked
      super(externalProjectsView, null, null);
    }

    @Override
    protected void update(PresentationData presentation) {
      super.update(presentation);
      presentation.setIcon(AllIcons.Nodes.PpLibFolder);
    }

    @Override
    public String getName() {
      return "Dependencies";
    }
  }

  private static class ModuleDependencyDataExternalSystemNode extends ExternalSystemNode<ModuleDependencyData> {

    public ModuleDependencyDataExternalSystemNode(ExternalProjectsView externalProjectsView, DataNode<ModuleDependencyData> dataNode) {
      super(externalProjectsView, null, dataNode);
    }

    @Override
    protected void update(PresentationData presentation) {
      super.update(presentation);
      presentation.setIcon(getUiAware().getProjectIcon());

      final ModuleDependencyData data = getData();
      if (data != null) {
        setNameAndTooltip(getName(), null, data.getScope().getDisplayName());
      }
    }

    @Override
    public String getName() {
      final ModuleDependencyData data = getData();
      return data != null ? data.getExternalName() : "";
    }

    @Override
    public boolean isAlwaysLeaf() {
      return true;
    }
  }

  private static class LibraryDependencyDataExternalSystemNode extends ExternalSystemNode<LibraryDependencyData> {

    public LibraryDependencyDataExternalSystemNode(ExternalProjectsView externalProjectsView, DataNode<LibraryDependencyData> dataNode) {
      super(externalProjectsView, null, dataNode);
    }

    @Override
    protected void update(PresentationData presentation) {
      super.update(presentation);
      presentation.setIcon(AllIcons.Nodes.PpLib);

      final LibraryDependencyData data = getData();
      if (data != null) {
        setNameAndTooltip(getName(), null, data.getScope().getDisplayName());
      }
    }

    @Override
    public String getName() {
      final LibraryDependencyData data = getData();
      return data != null ? data.getExternalName() : "";
    }

    @Override
    public boolean isAlwaysLeaf() {
      return true;
    }

    @Nullable
    @Override
    public Navigatable getNavigatable() {
      return new Navigatable() {
        @Nullable
        private OrderEntry myOrderEntry;

        @Override
        public void navigate(boolean requestFocus) {
          if (myOrderEntry != null) {
            ProjectSettingsService.getInstance(myProject).openLibraryOrSdkSettings(myOrderEntry);
          }
        }

        @Override
        public boolean canNavigate() {
          myOrderEntry = getOrderEntry();
          return myOrderEntry != null;
        }

        @Override
        public boolean canNavigateToSource() {
          return true;
        }
      };
    }

    @Nullable
    private OrderEntry getOrderEntry() {
      final LibraryDependencyData data = getData();
      if (data == null) return null;
      final Project project = getProject();
      if (project == null) return null;
      return ServiceManager.getService(ProjectStructureHelper.class).findIdeModuleOrderEntry(data, project);
    }
  }
}
