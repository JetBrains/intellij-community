/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Encapsulates functionality of importing external system module to the intellij project.
 *
 * @author Denis Zhdanov
 * @since 2/7/12 2:49 PM
 */
@Order(ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER)
public class ModuleDataService extends AbstractProjectDataService<ModuleData, Module> {

  public static final com.intellij.openapi.util.Key<ModuleData> MODULE_DATA_KEY = com.intellij.openapi.util.Key.create("MODULE_DATA_KEY");

  private static final Logger LOG = Logger.getInstance("#" + ModuleDataService.class.getName());

  @NotNull
  @Override
  public Key<ModuleData> getTargetDataKey() {
    return ProjectKeys.MODULE;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ModuleData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull PlatformFacade platformFacade,
                         boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }
    Collection<DataNode<ModuleData>> toCreate = filterExistingModules(toImport, project, platformFacade);
    if (!toCreate.isEmpty()) {
      ExternalSystemApiUtil.commitModels(synchronous, project, createModules(project, platformFacade, toCreate));
    }
    ExternalSystemApiUtil.commitModels(synchronous, project, syncModulesPaths(project, platformFacade, toImport));
  }

  @NotNull
  private static List<ModifiableRootModel> createModules(@NotNull Project project,
                                                         @NotNull PlatformFacade platformFacade,
                                                         Collection<DataNode<ModuleData>> toCreate) {
    List<ModifiableRootModel> models = ContainerUtilRt.newArrayList();
    try {
      for (DataNode<ModuleData> moduleData : toCreate) {
        models.add(createModule(project, platformFacade, moduleData));
      }
    }
    catch (Throwable t) {
      ExternalSystemApiUtil.disposeModels(models);
      ExceptionUtil.rethrowUnchecked(t);
    }
    return models;
  }

  private static ModifiableRootModel createModule(@NotNull Project project,
                                                  @NotNull PlatformFacade platformFacade,
                                                  @NotNull DataNode<ModuleData> module) {
    ModuleData data = module.getData();
    Module created = platformFacade.newModule(project, data.getModuleFilePath(), data.getModuleTypeId());

    // Ensure that the dependencies are clear (used to be not clear when manually removing the module and importing it via gradle)
    final ModifiableRootModel moduleRootModel = platformFacade.getModuleModifiableModel(created);
    moduleRootModel.inheritSdk();
    setModuleOptions(created, module);

    RootPolicy<Object> visitor = new RootPolicy<Object>() {
      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        moduleRootModel.removeOrderEntry(libraryOrderEntry);
        return value;
      }

      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        moduleRootModel.removeOrderEntry(moduleOrderEntry);
        return value;
      }
    };
    for (OrderEntry orderEntry : moduleRootModel.getOrderEntries()) {
      orderEntry.accept(visitor, null);
    }
    return moduleRootModel;
  }

  @NotNull
  private static Collection<DataNode<ModuleData>> filterExistingModules(@NotNull Collection<DataNode<ModuleData>> modules,
                                                                        @NotNull Project project,
                                                                        @NotNull PlatformFacade platformFacade)
  {
    Collection<DataNode<ModuleData>> result = ContainerUtilRt.newArrayList();
    for (DataNode<ModuleData> node : modules) {
      ModuleData moduleData = node.getData();
      Module module = platformFacade.findIdeModule(moduleData, project);
      if (module == null) {
        result.add(node);
      }
      else {
        setModuleOptions(module, node);
      }
    }
    return result;
  }

  @NotNull
  private List<ModifiableRootModel> syncModulesPaths(@NotNull Project project,
                                                     @NotNull PlatformFacade platformFacade,
                                                     Collection<DataNode<ModuleData>> toCreate) {
    List<ModifiableRootModel> models = ContainerUtilRt.newArrayList();
    try {
      for (DataNode<ModuleData> moduleData : toCreate) {
        Module module = platformFacade.findIdeModule(moduleData.getData(), project);
        if (module != null) {
          models.add(syncPaths(module, platformFacade, moduleData.getData()));
        }
      }
    }
    catch (Throwable t) {
      ExternalSystemApiUtil.disposeModels(models);
      ExceptionUtil.rethrowUnchecked(t);
    }
    return models;
  }

  @NotNull
  private static ModifiableRootModel syncPaths(@NotNull Module module, @NotNull PlatformFacade platformFacade, @NotNull ModuleData data) {
    ModifiableRootModel modifiableModel = platformFacade.getModuleModifiableModel(module);
    CompilerModuleExtension extension = modifiableModel.getModuleExtension(CompilerModuleExtension.class);
    if (extension == null) {
      LOG.warn(String.format("Can't sync paths for module '%s'. Reason: no compiler extension is found for it", module.getName()));
      return modifiableModel;
    }
    String compileOutputPath = data.getCompileOutputPath(ExternalSystemSourceType.SOURCE);
    if (compileOutputPath != null) {
      extension.setCompilerOutputPath(VfsUtilCore.pathToUrl(compileOutputPath));
    }

    String testCompileOutputPath = data.getCompileOutputPath(ExternalSystemSourceType.TEST);
    if (testCompileOutputPath != null) {
      extension.setCompilerOutputPathForTests(VfsUtilCore.pathToUrl(testCompileOutputPath));
    }

    extension.inheritCompilerOutputPath(data.isInheritProjectCompileOutputPath());
    return modifiableModel;
  }

  @NotNull
  @Override
  public Computable<Collection<Module>> computeOrphanData(@NotNull final Collection<DataNode<ModuleData>> toImport,
                                                          @NotNull final ProjectData projectData,
                                                          @NotNull final Project project,
                                                          @NotNull final PlatformFacade platformFacade) {
    return new Computable<Collection<Module>>() {
      @Override
      public Collection<Module> compute() {
        List<Module> orphanIdeModules = ContainerUtil.newSmartList();

        for (Module module : platformFacade.getModules(project)) {
          if (!ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner(), module)) continue;
          final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
          if (projectData.getLinkedExternalProjectPath().equals(rootProjectPath)) {
            final String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
            final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);

            final DataNode<ModuleData> found = ContainerUtil.find(toImport, new Condition<DataNode<ModuleData>>() {
              @Override
              public boolean value(DataNode<ModuleData> node) {
                final ModuleData moduleData = node.getData();
                return moduleData.getId().equals(projectId) && moduleData.getLinkedExternalProjectPath().equals(projectPath);
              }
            });

            if (found == null) {
              orphanIdeModules.add(module);
            }
          }
        }

        return orphanIdeModules;
      }
    };
  }

  @Override
  public void removeData(@NotNull final Computable<Collection<Module>> toRemoveComputable,
                         @NotNull final Collection<DataNode<ModuleData>> toIgnore,
                         @NotNull final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final PlatformFacade platformFacade,
                         final boolean synchronous) {
    final Collection<Module> toRemove = toRemoveComputable.compute();
    final List<Module> modules = new SmartList<Module>(toRemove);
    for (DataNode<ModuleData> moduleDataNode : toIgnore) {
      final Module module = platformFacade.findIdeModule(moduleDataNode.getData(), project);
      ContainerUtil.addIfNotNull(modules, module);
    }

    if (modules.isEmpty()) {
      return;
    }

    ContainerUtil.removeDuplicates(modules);

    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        for (Module module : modules) {
          if (module.isDisposed()) continue;
          unlinkModuleFromExternalSystem(module);
        }
      }
    });

    ruleOrphanModules(modules, project, projectData.getOwner(), new Consumer<List<Module>>() {
      @Override
      public void consume(final List<Module> modules) {
        ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
          @Override
          public void execute() {
            for (Module module : modules) {
              if (module.isDisposed()) continue;

              ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
              String path = module.getModuleFilePath();
              moduleManager.disposeModule(module);
              File file = new File(path);
              if (file.isFile()) {
                boolean success = file.delete();
                if (!success) {
                  LOG.warn("Can't remove module file at '" + path + "'");
                }
              }
            }
          }
        });
      }
    });
  }

  /**
   * There is a possible case that an external module has been un-linked from ide project. There are two ways to process
   * ide modules which correspond to that external project:
   * <pre>
   * <ol>
   *   <li>Remove them from ide project as well;</li>
   *   <li>Keep them at ide project as well;</li>
   * </ol>
   * </pre>
   * This method handles that situation, i.e. it asks a user what should be done and acts accordingly.
   *
   * @param orphanModules    modules which correspond to the un-linked external project
   * @param project          current ide project
   * @param externalSystemId id of the external system which project has been un-linked from ide project
   */
  private static void ruleOrphanModules(@NotNull final List<Module> orphanModules,
                                        @NotNull final Project project,
                                        @NotNull final ProjectSystemId externalSystemId,
                                        @NotNull final Consumer<List<Module>> result) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        List<Module> toRemove = ContainerUtil.newSmartList();
        if(ApplicationManager.getApplication().isHeadlessEnvironment()) {
          toRemove.addAll(orphanModules);
        } else {
          final JPanel content = new JPanel(new GridBagLayout());
          content.add(new JLabel(ExternalSystemBundle.message("orphan.modules.text", externalSystemId.getReadableName())),
                      ExternalSystemUiUtil.getFillLineConstraints(0));

          final CheckBoxList<Module> orphanModulesList = new CheckBoxList<Module>();
          orphanModulesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
          orphanModulesList.setItems(orphanModules, new Function<Module, String>() {
            @Override
            public String fun(Module module) {
              return module.getName();
            }
          });
          for (Module module : orphanModules) {
            orphanModulesList.setItemSelected(module, true);
          }
          orphanModulesList.setBorder(IdeBorderFactory.createEmptyBorder(8));
          content.add(orphanModulesList, ExternalSystemUiUtil.getFillLineConstraints(0));
          content.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 8, 0));

          DialogWrapper dialog = new DialogWrapper(project) {
            {
              setTitle(ExternalSystemBundle.message("import.title", externalSystemId.getReadableName()));
              init();
            }

            @Nullable
            @Override
            protected JComponent createCenterPanel() {
              return new JBScrollPane(content);
            }

            @NotNull
            protected Action[] createActions() {
              return new Action[]{getOKAction()};
            }
          };

          dialog.showAndGet();

          for (int i = 0; i < orphanModules.size(); i++) {
            Module module = orphanModules.get(i);
            if (orphanModulesList.isItemSelected(i)) {
              toRemove.add(module);
            }
          }
        }
        result.consume(toRemove);
      }
    });
  }

  public static void unlinkModuleFromExternalSystem(@NotNull Module module) {
    module.clearOption(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
    module.clearOption(ExternalSystemConstants.LINKED_PROJECT_ID_KEY);
    module.clearOption(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
    module.clearOption(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
    module.clearOption(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_GROUP_KEY);
    module.clearOption(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_VERSION_KEY);
  }

  private static void setModuleOptions(Module module, DataNode<ModuleData> moduleDataNode) {
    ModuleData moduleData = moduleDataNode.getData();
    module.putUserData(MODULE_DATA_KEY, moduleData);

    module.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, moduleData.getOwner().toString());
    module.setOption(ExternalSystemConstants.LINKED_PROJECT_ID_KEY, moduleData.getId());
    module.setOption(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY, moduleData.getLinkedExternalProjectPath());
    ProjectData projectData = moduleDataNode.getData(ProjectKeys.PROJECT);
    module.setOption(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY, projectData != null ? projectData.getLinkedExternalProjectPath() : "");

    if (moduleData.getGroup() != null) {
      module.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_GROUP_KEY, moduleData.getGroup());
    }
    if (moduleData.getVersion() != null) {
      module.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_MODULE_VERSION_KEY, moduleData.getVersion());
    }

    // clear maven option
    module.clearOption("org.jetbrains.idea.maven.project.MavenProjectsManager.isMavenModule");
  }
}
