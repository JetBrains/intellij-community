/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/5/2015
 */
public abstract class AbstractModuleDataService<E extends ModuleData> extends AbstractProjectDataService<E, Module> {

  public static final Key<ModuleData> MODULE_DATA_KEY = Key.create("MODULE_DATA_KEY");
  public static final Key<Module> MODULE_KEY = Key.create("LINKED_MODULE");
  public static final Key<Map<OrderEntry, OrderAware>> ORDERED_DATA_MAP_KEY = Key.create("ORDER_ENTRY_DATA_MAP");
  public static final Key<Set<String>> ORPHAN_MODULE_FILES = Key.create("ORPHAN_FILES");

  private static final Logger LOG = Logger.getInstance(AbstractModuleDataService.class);

  @Override
  public void importData(@NotNull final Collection<DataNode<E>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      return;
    }

    final Collection<DataNode<E>> toCreate = filterExistingModules(toImport, modelsProvider);
    if (!toCreate.isEmpty()) {
      createModules(toCreate, modelsProvider, project);
    }

    for (DataNode<E> node : toImport) {
      Module module = node.getUserData(MODULE_KEY);
      if (module != null) {
        ProjectCoordinate publication = node.getData().getPublication();
        if (publication != null){
          modelsProvider.registerModulePublication(module, publication);
        }
        String productionModuleId = node.getData().getProductionModuleId();
        modelsProvider.setTestModuleProperties(module, productionModuleId);
        setModuleOptions(module, node);
        ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
        syncPaths(module, modifiableRootModel, node.getData());
        setLanguageLevel(modifiableRootModel, node.getData());
        setSdk(modifiableRootModel, node.getData());
      }
    }

    for (DataNode<E> node : toImport) {
      Module module = node.getUserData(MODULE_KEY);
      if (module != null) {
        final String[] groupPath;
        groupPath = node.getData().getIdeModuleGroup();
        final ModifiableModuleModel modifiableModel = modelsProvider.getModifiableModuleModel();
        modifiableModel.setModuleGroupPath(module, groupPath);
      }
    }
  }

  private void createModules(@NotNull Collection<DataNode<E>> toCreate,
                             @NotNull IdeModifiableModelsProvider modelsProvider,
                             @NotNull Project project) {
    for (final DataNode<E> module : toCreate) {
      ModuleData data = module.getData();
      final Module created = modelsProvider.newModule(data);
      module.putUserData(MODULE_KEY, created);
      Set<String> orphanFiles = project.getUserData(ORPHAN_MODULE_FILES);
      if (orphanFiles != null) {
        orphanFiles.remove(created.getModuleFilePath());
      }

      // Ensure that the dependencies are clear (used to be not clear when manually removing the module and importing it via external system)
      final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(created);
      modifiableRootModel.inheritSdk();

      RootPolicy<Object> visitor = new RootPolicy<Object>() {
        @Override
        public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
          modifiableRootModel.removeOrderEntry(libraryOrderEntry);
          return value;
        }

        @Override
        public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
          modifiableRootModel.removeOrderEntry(moduleOrderEntry);
          return value;
        }
      };

      for (OrderEntry orderEntry : modifiableRootModel.getOrderEntries()) {
        orderEntry.accept(visitor, null);
      }
    }
  }

  @NotNull
  private Collection<DataNode<E>> filterExistingModules(@NotNull Collection<DataNode<E>> modules,
                                                        @NotNull IdeModifiableModelsProvider modelsProvider) {
    Collection<DataNode<E>> result = ContainerUtilRt.newArrayList();
    for (DataNode<E> node : modules) {
      ModuleData moduleData = node.getData();
      Module module = modelsProvider.findIdeModule(moduleData);
      if (module == null) {
        UnloadedModuleDescription unloadedModuleDescription = modelsProvider.getUnloadedModuleDescription(moduleData);
        if (unloadedModuleDescription == null) {
          result.add(node);
        }
      }
      else {
        node.putUserData(MODULE_KEY, module);
      }
    }
    return result;
  }

  private static void syncPaths(@NotNull Module module, @NotNull ModifiableRootModel modifiableModel, @NotNull ModuleData data) {
    CompilerModuleExtension extension = modifiableModel.getModuleExtension(CompilerModuleExtension.class);
    if (extension == null) {
      //modifiableModel.dispose();
      LOG.warn(String.format("Can't sync paths for module '%s'. Reason: no compiler extension is found for it", module.getName()));
      return;
    }
    String compileOutputPath = data.getCompileOutputPath(ExternalSystemSourceType.SOURCE);
    extension.setCompilerOutputPath(compileOutputPath != null ? VfsUtilCore.pathToUrl(compileOutputPath) : null);

    String testCompileOutputPath = data.getCompileOutputPath(ExternalSystemSourceType.TEST);
    extension.setCompilerOutputPathForTests(testCompileOutputPath != null ? VfsUtilCore.pathToUrl(testCompileOutputPath) : null);

    extension.inheritCompilerOutputPath(data.isInheritProjectCompileOutputPath());
  }

  @Override
  public void removeData(@NotNull final Computable<Collection<Module>> toRemoveComputable,
                         @NotNull final Collection<DataNode<E>> toIgnore,
                         @NotNull final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    final Collection<Module> toRemove = toRemoveComputable.compute();
    final List<Module> modules = new SmartList<>(toRemove);
    for (DataNode<E> moduleDataNode : toIgnore) {
      final Module module = modelsProvider.findIdeModule(moduleDataNode.getData());
      ContainerUtil.addIfNotNull(modules, module);
    }

    if (modules.isEmpty()) {
      return;
    }

    ContainerUtil.removeDuplicates(modules);

    for (Module module : modules) {
      if (module.isDisposed()) continue;
      unlinkModuleFromExternalSystem(module);
    }

    ruleOrphanModules(modules, project, projectData.getOwner(), modules1 -> {
      for (Module module : modules1) {
        if (module.isDisposed()) continue;
        String path = module.getModuleFilePath();
        final ModifiableModuleModel moduleModel = modelsProvider.getModifiableModuleModel();
        moduleModel.disposeModule(module);
        ModuleBuilder.deleteModuleFile(path);
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
    ExternalSystemApiUtil.executeOnEdt(true, () -> {
      List<Module> toRemove = ContainerUtil.newSmartList();
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        toRemove.addAll(orphanModules);
      }
      else {
        final JPanel content = new JPanel(new GridBagLayout());
        content.add(new JLabel(ExternalSystemBundle.message("orphan.modules.text", externalSystemId.getReadableName())),
                    ExternalSystemUiUtil.getFillLineConstraints(0));

        final CheckBoxList<Module> orphanModulesList = new CheckBoxList<>();
        orphanModulesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        orphanModulesList.setItems(orphanModules, module -> module.getName());
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
    });
  }

  public static void unlinkModuleFromExternalSystem(@NotNull Module module) {
    ExternalSystemModulePropertyManager.getInstance(module).unlinkExternalOptions();
  }

  protected void setModuleOptions(Module module, DataNode<E> moduleDataNode) {
    ModuleData moduleData = moduleDataNode.getData();
    module.putUserData(MODULE_DATA_KEY, moduleData);
    ExternalSystemModulePropertyManager.getInstance(module).setExternalOptions(moduleData.getOwner(), moduleData, moduleDataNode.getData(ProjectKeys.PROJECT));
  }

  @Override
  public void postProcess(@NotNull Collection<DataNode<E>> toImport,
                          @Nullable ProjectData projectData,
                          @NotNull Project project,
                          @NotNull IdeModifiableModelsProvider modelsProvider) {
    for (DataNode<E> moduleDataNode : toImport) {
      final Module module = moduleDataNode.getUserData(MODULE_KEY);
      if (module == null) continue;
      final Map<OrderEntry, OrderAware> orderAwareMap = moduleDataNode.getUserData(ORDERED_DATA_MAP_KEY);
      if (orderAwareMap != null) {
        rearrangeOrderEntries(orderAwareMap, modelsProvider.getModifiableRootModel(module));
      }
      setBytecodeTargetLevel(project, module, moduleDataNode.getData());
    }

    for (Module module : modelsProvider.getModules()) {
      module.putUserData(MODULE_DATA_KEY, null);
    }
  }

  @Override
  public void onSuccessImport(@NotNull Collection<DataNode<E>> imported,
                              @Nullable ProjectData projectData,
                              @NotNull Project project,
                              @NotNull IdeModelsProvider modelsProvider) {
    final Set<String> orphanFiles = project.getUserData(ORPHAN_MODULE_FILES);
    if (orphanFiles != null && !orphanFiles.isEmpty()) {
      ExternalSystemApiUtil.executeOnEdt(false, () -> {
        for (String orphanFile : orphanFiles) {
          ModuleBuilder.deleteModuleFile(orphanFile);
        }
      });
      project.putUserData(ORPHAN_MODULE_FILES, null);
    }
  }

  protected void rearrangeOrderEntries(@NotNull Map<OrderEntry, OrderAware> orderEntryDataMap,
                                       @NotNull ModifiableRootModel modifiableRootModel) {
    final OrderEntry[] orderEntries = modifiableRootModel.getOrderEntries();
    final int length = orderEntries.length;
    final OrderEntry[] newOrder = new OrderEntry[length];
    final PriorityQueue<Pair<OrderEntry, OrderAware>> priorityQueue = new PriorityQueue<>(
      11, (o1, o2) -> {
      int order1 = o1.second.getOrder();
      int order2 = o2.second.getOrder();
      return order1 != order2 ? order1 < order2 ? -1 : 1 : 0;
    });

    int shift = 0;
    for (int i = 0; i < length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      final OrderAware orderAware = orderEntryDataMap.get(orderEntry);
      if (orderAware == null) {
        newOrder[i] = orderEntry;
        shift++;
      }
      else {
        priorityQueue.add(Pair.create(orderEntry, orderAware));
      }
    }

    Pair<OrderEntry, OrderAware> pair;
    while ((pair = priorityQueue.poll()) != null) {
      final OrderEntry orderEntry = pair.first;
      final OrderAware orderAware = pair.second;
      final int order = orderAware.getOrder() != -1 ? orderAware.getOrder() : length - 1;
      final int newPlace = findNewPlace(newOrder, order - shift);
      assert newPlace != -1;
      newOrder[newPlace] = orderEntry;
    }

    if (LOG.isDebugEnabled()) {
      final boolean changed = !ArrayUtil.equals(orderEntries, newOrder, Comparator.naturalOrder());
      LOG.debug(String.format("rearrange status (%s): %s", modifiableRootModel.getModule(), changed ? "modified" : "not modified"));
    }
    modifiableRootModel.rearrangeOrderEntries(newOrder);
  }

  private static int findNewPlace(OrderEntry[] newOrder, int newIndex) {
    int idx = newIndex;
    while (idx < 0 || (idx < newOrder.length && newOrder[idx] != null)) {
      idx++;
    }
    if (idx >= newOrder.length) {
      idx = newIndex - 1;
      while (idx >= 0 && (idx >= newOrder.length || newOrder[idx] != null)) {
        idx--;
      }
    }
    return idx;
  }

  private void setLanguageLevel(@NotNull ModifiableRootModel modifiableRootModel, E data) {
    LanguageLevel level = LanguageLevel.parse(data.getSourceCompatibility());
    if (level != null) {
      try {
        modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
      }
      catch (IllegalArgumentException e) {
        LOG.debug(e);
      }
    }
  }

  private void setSdk(@NotNull ModifiableRootModel modifiableRootModel, E data) {
    String skdName = data.getSdkName();
    if (skdName != null) {
      ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
      Sdk sdk = projectJdkTable.findJdk(skdName);
      if (sdk != null) {
        modifiableRootModel.setSdk(sdk);
      } else {
        modifiableRootModel.setInvalidSdk(skdName, JavaSdk.getInstance().getName());
      }
    }
  }

  private void setBytecodeTargetLevel(@NotNull Project project, @NotNull Module module, @NotNull E data) {
    String targetLevel = data.getTargetCompatibility();
    if (targetLevel != null) {
      CompilerConfiguration configuration = CompilerConfiguration.getInstance(project);
      configuration.setBytecodeTargetLevel(module, targetLevel);
    }
  }
}
