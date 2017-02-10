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
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.PROJECT;

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
  public ProjectSystemId getSystemId() {
    return ProjectSystemId.IDE;
  }

  @NotNull
  @Override
  public List<Key<?>> getKeys() {
    return Arrays.asList(KEYS);
  }

  @Override
  @NotNull
  public List<ExternalSystemNode<?>> createNodes(final ExternalProjectsView externalProjectsView,
                                                 final MultiMap<Key<?>, DataNode<?>> dataNodes) {
    final List<ExternalSystemNode<?>> result = new SmartList<>();

    addModuleNodes(externalProjectsView, dataNodes, result);
    // add tasks
    Collection<DataNode<?>> tasksNodes = dataNodes.get(ProjectKeys.TASK);
    if (!tasksNodes.isEmpty()) {
      TasksNode tasksNode = new TasksNode(externalProjectsView, tasksNodes);
      if (externalProjectsView.useTasksNode()) {
        result.add(tasksNode);
      }
      else {
        ContainerUtil.addAll(result, tasksNode.getChildren());
      }
    }

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
        ModuleDependencyDataExternalSystemNode moduleDependencyDataExternalSystemNode =
          new ModuleDependencyDataExternalSystemNode(externalProjectsView, (DataNode<ModuleDependencyData>)dataNode);
        if (dataNode.getParent() != null && dataNode.getParent().getData() instanceof AbstractDependencyData) {
          result.add(moduleDependencyDataExternalSystemNode);
        }
        else {
          depNode.add(moduleDependencyDataExternalSystemNode);
        }
      }

      for (DataNode<?> dataNode : libDeps) {
        if (!(dataNode.getData() instanceof LibraryDependencyData)) continue;
        //noinspection unchecked
        final ExternalSystemNode<LibraryDependencyData> libraryDependencyDataExternalSystemNode =
          new LibraryDependencyDataExternalSystemNode(externalProjectsView, (DataNode<LibraryDependencyData>)dataNode);
        if (((LibraryDependencyData)dataNode.getData()).getTarget().isUnresolved()) {
          libraryDependencyDataExternalSystemNode.setErrorLevel(
            ExternalProjectsStructure.ErrorLevel.ERROR,
            "Unable to resolve " + ((LibraryDependencyData)dataNode.getData()).getTarget().getExternalName());
        }
        else {
          libraryDependencyDataExternalSystemNode.setErrorLevel(ExternalProjectsStructure.ErrorLevel.NONE);
        }
        if (dataNode.getParent() != null && dataNode.getParent().getData() instanceof ModuleData) {
          depNode.add(libraryDependencyDataExternalSystemNode);
        }
        else {
          result.add(libraryDependencyDataExternalSystemNode);
        }
      }

      if (depNode.hasChildren()) {
        result.add(depNode);
      }
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
        DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(dataNode, PROJECT);
        final boolean isRoot =
          projectSettings != null && data.getLinkedExternalProjectPath().equals(projectSettings.getExternalProjectPath()) &&
          projectDataNode != null && projectDataNode.getData().getInternalName().equals(data.getInternalName());
        //noinspection unchecked
        final ModuleNode moduleNode = new ModuleNode(externalProjectsView, (DataNode<ModuleData>)dataNode, isRoot);
        result.add(moduleNode);
      }
    }
  }

  @Order(ExternalSystemNode.BUILTIN_DEPENDENCIES_DATA_NODE_ORDER)
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

  private static abstract class DependencyDataExternalSystemNode<T extends DependencyData> extends ExternalSystemNode<T> {

    public DependencyDataExternalSystemNode(@NotNull ExternalProjectsView externalProjectsView,
                                            @Nullable ExternalSystemNode parent,
                                            @Nullable DataNode<T> dataNode) {
      super(externalProjectsView, parent, dataNode);
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
            ProjectSettingsService.getInstance(myProject).openModuleDependenciesSettings(myOrderEntry.getOwnerModule(), myOrderEntry);
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
      final T data = getData();
      if (data == null) return null;
      final Project project = getProject();
      if (project == null) return null;
      return new IdeModelsProviderImpl(project).findIdeModuleOrderEntry(data);
    }

    @Override
    public int compareTo(@NotNull ExternalSystemNode node) {
      final T myData = getData();
      final Object thatData = node.getData();
      if (myData instanceof OrderAware && thatData instanceof OrderAware) {
        int order1 = ((OrderAware)myData).getOrder();
        int order2 = ((OrderAware)thatData).getOrder();
        if (order1 != order2) {
          return order1 < order2 ? -1 : 1;
        }
      }

      String dependencyName = getDependencySimpleName(this);
      String thatDependencyName = getDependencySimpleName(node);
      return StringUtil.compare(dependencyName, thatDependencyName, true);
    }

    @NotNull
    private static String getDependencySimpleName(@NotNull ExternalSystemNode node) {
      Object thatData = node.getData();
      if (thatData instanceof LibraryDependencyData) {
        LibraryDependencyData dependencyData = (LibraryDependencyData)thatData;
        String externalName = dependencyData.getExternalName();
        if (StringUtil.isEmpty(externalName)) {
          Set<String> paths = dependencyData.getTarget().getPaths(LibraryPathType.BINARY);
          if (paths.size() == 1) {
            return new File(paths.iterator().next()).getName();
          }
        }
      }
      return node.getName();
    }
  }

  private static class ModuleDependencyDataExternalSystemNode extends DependencyDataExternalSystemNode<ModuleDependencyData> {

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
      return false;
    }
  }

  private static class LibraryDependencyDataExternalSystemNode extends DependencyDataExternalSystemNode<LibraryDependencyData> {

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
      if (data == null) return "";
      String externalName = data.getExternalName();
      if (StringUtil.isEmpty(externalName)) {
        Set<String> paths = data.getTarget().getPaths(LibraryPathType.BINARY);
        if (paths.size() == 1) {
          String relativePathToRoot = null;
          String path = ExternalSystemApiUtil.toCanonicalPath(paths.iterator().next());
          DataNode<ProjectData> projectDataDataNode = ExternalSystemApiUtil.findParent(myDataNode, PROJECT);
          if (projectDataDataNode != null) {
            relativePathToRoot = FileUtil.getRelativePath(projectDataDataNode.getData().getLinkedExternalProjectPath(), path, '/');
            relativePathToRoot = relativePathToRoot != null && StringUtil.startsWith(relativePathToRoot, "../../")
                                 ? new File(relativePathToRoot).getName()
                                 : relativePathToRoot;
          }
          return ObjectUtils.notNull(relativePathToRoot, path);
        }
        else {
          return "<file set>";
        }
      }
      return externalName;
    }

    @Override
    public boolean isAlwaysLeaf() {
      return false;
    }
  }
}
