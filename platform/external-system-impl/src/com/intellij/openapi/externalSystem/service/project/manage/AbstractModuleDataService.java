// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings.SyncType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractModuleDataService<E extends ModuleData> extends AbstractProjectDataService<E, Module> {
  public static final Key<ModuleData> MODULE_DATA_KEY = Key.create("MODULE_DATA_KEY");
  public static final Key<Module> MODULE_KEY = Key.create("LINKED_MODULE");
  public static final Key<Map<OrderEntry, OrderAware>> ORDERED_DATA_MAP_KEY = Key.create("ORDER_ENTRY_DATA_MAP");
  private static final Key<Set<Path>> ORPHAN_MODULE_FILES = Key.create("ORPHAN_FILES");
  private static final Key<AtomicInteger> ORPHAN_MODULE_HANDLERS_COUNTER = Key.create("ORPHAN_MODULE_HANDLERS_COUNTER");

  private static final Logger LOG = Logger.getInstance(AbstractModuleDataService.class);

  @Override
  public void importData(final @NotNull Collection<? extends DataNode<E>> toImport,
                         @Nullable ProjectData projectData,
                         final @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      return;
    }

    final Collection<DataNode<E>> toCreate = filterExistingModules(toImport, modelsProvider);
    if (!toCreate.isEmpty()) {
      createModules(toCreate, modelsProvider);
    }

    for (DataNode<E> node : toImport) {
      Module module = node.getUserData(MODULE_KEY);
      if (module != null) {
        String productionModuleId = node.getData().getProductionModuleId();
        modelsProvider.setTestModuleProperties(module, productionModuleId);
        setModuleOptions(module, node);
        ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
        syncPaths(module, modifiableRootModel, node.getData());
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

  protected @NotNull Module createModule(@NotNull DataNode<E> module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    ModuleData data = module.getData();
    return modelsProvider.newModule(data);
  }

  private void createModules(@NotNull Collection<? extends DataNode<E>> toCreate, @NotNull IdeModifiableModelsProvider modelsProvider) {
    for (DataNode<E> module : toCreate) {
      Module created = createModule(module, modelsProvider);
      module.putUserData(MODULE_KEY, created);

      // Ensure that the dependencies are clear (used to be not clear when manually removing the module and importing it via external system)
      final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(created);

      RootPolicy<Object> visitor = new RootPolicy<>() {
        @Override
        public Object visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry, Object value) {
          modifiableRootModel.removeOrderEntry(libraryOrderEntry);
          return value;
        }

        @Override
        public Object visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, Object value) {
          modifiableRootModel.removeOrderEntry(moduleOrderEntry);
          return value;
        }
      };

      for (OrderEntry orderEntry : modifiableRootModel.getOrderEntries()) {
        orderEntry.accept(visitor, null);
      }
    }
  }

  private @NotNull Collection<DataNode<E>> filterExistingModules(@NotNull Collection<? extends DataNode<E>> modules,
                                                                 @NotNull IdeModifiableModelsProvider modelsProvider) {
    Collection<DataNode<E>> result = new ArrayList<>();
    for (DataNode<E> node : modules) {
      ModuleData moduleData = node.getData();
      Module module = modelsProvider.findIdeModule(moduleData);
      if (module == null) {
        UnloadedModuleDescription unloadedModuleDescription = modelsProvider.getUnloadedModuleDescription(moduleData);
        if (unloadedModuleDescription == null) {
          result.add(node);
        }
        markExistedModulesWithSameRoot(node, modelsProvider);
      }
      else {
        node.putUserData(MODULE_KEY, module);
      }
    }
    return result;
  }

  private void markExistedModulesWithSameRoot(DataNode<E> node,
                                              IdeModifiableModelsProvider modelsProvider) {
    ModuleData moduleData = node.getData();
    ProjectSystemId projectSystemId = moduleData.getOwner();
    Arrays.stream(modelsProvider.getModules())
      .filter(ideModule -> isModulePointsSameRoot(moduleData, ideModule))
      .filter(module -> !ExternalSystemApiUtil.isExternalSystemAwareModule(projectSystemId, module))
      .forEach(module -> {
        ExternalSystemModulePropertyManager.getInstance(module)
          .setExternalOptions(projectSystemId, moduleData, node.getData(ProjectKeys.PROJECT));
      });

  }

  private static boolean isModulePointsSameRoot(ModuleData moduleData, Module ideModule) {
    for (VirtualFile root: ModuleRootManager.getInstance(ideModule).getContentRoots()) {
      if (VfsUtilCore.pathEqualsTo(root, moduleData.getLinkedExternalProjectPath())) {
        return true;
      }
    }
    return false;
  }

  private static void syncPaths(@NotNull Module module, @NotNull ModifiableRootModel modifiableModel, @NotNull ModuleData data) {
    CompilerModuleExtension extension = modifiableModel.getModuleExtension(CompilerModuleExtension.class);
    if (extension == null) {
      LOG.debug(String.format("No compiler extension is found for '%s', compiler output path will not be synced.", module.getName()));
      return;
    }
    String compileOutputPath = data.getCompileOutputPath(ExternalSystemSourceType.SOURCE);
    extension.setCompilerOutputPath(compileOutputPath != null ? VfsUtilCore.pathToUrl(compileOutputPath) : null);

    String testCompileOutputPath = data.getCompileOutputPath(ExternalSystemSourceType.TEST);
    extension.setCompilerOutputPathForTests(testCompileOutputPath != null ? VfsUtilCore.pathToUrl(testCompileOutputPath) : null);

    extension.inheritCompilerOutputPath(data.isInheritProjectCompileOutputPath());
  }

  @Override
  public void removeData(Computable<? extends Collection<? extends Module>> toRemoveComputable,
                         final @NotNull Collection<? extends DataNode<E>> toIgnore,
                         final @NotNull ProjectData projectData,
                         final @NotNull Project project,
                         final @NotNull IdeModifiableModelsProvider modelsProvider) {
    final Collection<? extends Module> toRemove = toRemoveComputable.compute();
    final List<Module> modules = new SmartList<>(toRemove);
    for (DataNode<E> moduleDataNode : toIgnore) {
      final Module module = modelsProvider.findIdeModule(moduleDataNode.getData());
      ContainerUtil.addIfNotNull(modules, module);
    }

    if (modules.isEmpty()) {
      return;
    }

    ContainerUtil.removeDuplicates(modules);

    ProjectSystemId projectSystemId = projectData.getOwner();
    ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove external system options from workspace model", __ -> {
      for (Module module : modules) {
        if (module.isDisposed()) continue;
        ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove options for " + module.getName(), ___ -> {
          unlinkModuleFromExternalSystem(module);
        });
      }
    });

    ExternalSystemApiUtil.executeOnEdt(true, () -> {
      AtomicInteger counter = project.getUserData(ORPHAN_MODULE_HANDLERS_COUNTER);
      if (counter == null) {
        counter = new AtomicInteger();
        project.putUserData(ORPHAN_MODULE_HANDLERS_COUNTER, counter);
      }
      counter.incrementAndGet();

      Set<Path> orphanModules = project.getUserData(ORPHAN_MODULE_FILES);
      if (orphanModules == null) {
        orphanModules = new LinkedHashSet<>();
        project.putUserData(ORPHAN_MODULE_FILES, orphanModules);
      }

      LocalHistoryAction historyAction =
        LocalHistory.getInstance().startAction(ExternalSystemBundle.message("local.history.remove.orphan.modules"));
      try {
        String rootProjectPathKey = String.valueOf(projectData.getLinkedExternalProjectPath().hashCode());
        Path unlinkedModulesDir =
          ExternalProjectsDataStorage.getProjectConfigurationDir(project).resolve("orphanModules").resolve(rootProjectPathKey);
        if (!FileUtil.createDirectory(unlinkedModulesDir.toFile())) {
          LOG.warn("Unable to create " + unlinkedModulesDir);
          return;
        }

        AbstractExternalSystemLocalSettings<?> localSettings = ExternalSystemApiUtil.getLocalSettings(project, projectData.getOwner());
        SyncType syncType = localSettings.getProjectSyncType().get(projectData.getLinkedExternalProjectPath());
        for (Module module : modules) {
          if (module.isDisposed()) continue;
          String path = module.getModuleFilePath();
          if (!ApplicationManager.getApplication().isHeadlessEnvironment() && syncType == SyncType.RE_IMPORT) {
            try {
              // we need to save module configuration before dispose, to get the up-to-date content of the unlinked module iml
              StoreUtil.saveSettings(module);
              VirtualFile moduleFile = module.getModuleFile();
              if (moduleFile != null) {
                Path orphanModulePath = unlinkedModulesDir.resolve(String.valueOf(path.hashCode()));
                FileUtil.writeToFile(orphanModulePath.toFile(), moduleFile.contentsToByteArray());
                Path orphanModuleOriginPath = unlinkedModulesDir.resolve(path.hashCode() + ".path");
                FileUtil.writeToFile(orphanModuleOriginPath.toFile(), path);
                orphanModules.add(orphanModulePath);
              }
            }
            catch (Exception e) {
              LOG.warn(e);
            }
          }
          modelsProvider.getModifiableModuleModel().disposeModule(module);
        }
      }
      finally {
        historyAction.finish();
      }
    });
  }

  @Override
  public void onSuccessImport(@NotNull Collection<DataNode<E>> imported,
                              @Nullable ProjectData projectData,
                              @NotNull Project project,
                              @NotNull IdeModelsProvider modelsProvider) {
    Set<Path> orphanModules = project.getUserData(ORPHAN_MODULE_FILES);
    if (orphanModules == null || orphanModules.isEmpty()) {
      return;
    }

    AtomicInteger counter = project.getUserData(ORPHAN_MODULE_HANDLERS_COUNTER);
    if (counter == null) {
      return;
    }
    if (counter.decrementAndGet() == 0) {
      project.putUserData(ORPHAN_MODULE_FILES, null);
      project.putUserData(ORPHAN_MODULE_HANDLERS_COUNTER, null);
      StringBuilder modulesToRestoreText = new StringBuilder();
      List<Pair<String, Path>> modulesToRestore = new ArrayList<>();
      for (Path modulePath : orphanModules) {
        try {
          String path = FileUtil.loadFile(modulePath.resolveSibling(modulePath.getFileName() + ".path").toFile());
          modulesToRestoreText.append(FileUtilRt.getNameWithoutExtension(new File(path).getName())).append("\n");
          modulesToRestore.add(Pair.create(path, modulePath));
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }

      String buildSystem = projectData != null ? projectData.getOwner().getReadableName() : "build system";
      String content = ExternalSystemBundle.message("orphan.modules.text", buildSystem,
                                                    StringUtil.shortenTextWithEllipsis(modulesToRestoreText.toString(), 50, 0));
      Notification cleanUpNotification = NotificationGroupManager.getInstance().getNotificationGroup("Build sync orphan modules")
        .createNotification(content, NotificationType.INFORMATION)
        .setListener((notification, event) -> {
          if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
          if (showRemovedOrphanModules(modulesToRestore, project)) {
            notification.expire();
          }
        })
        .whenExpired(() -> {
          List<File> filesToRemove = ContainerUtil.map(orphanModules, Path::toFile);
          List<File> toRemove2 = ContainerUtil.map(orphanModules, path -> path.resolveSibling(path.getFileName() + ".path").toFile());

          FileUtil.asyncDelete(ContainerUtil.concat(filesToRemove, toRemove2));
        });

      Disposer.register(project, cleanUpNotification::expire);
      cleanUpNotification.notify(project);
    }
  }

  @Override
  public void onFailureImport(Project project) {
    project.putUserData(ORPHAN_MODULE_FILES, null);
    project.putUserData(ORPHAN_MODULE_HANDLERS_COUNTER, null);
  }

  private static boolean showRemovedOrphanModules(final @NotNull List<? extends Pair<String, Path>> orphanModules,
                                                  final @NotNull Project project) {
    final CheckBoxList<Pair<String, Path>> orphanModulesList = new CheckBoxList<>();
    DialogWrapper dialog = new DialogWrapper(project) {
      {
        setTitle(ExternalSystemBundle.message("orphan.modules.dialog.title"));
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        orphanModulesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        orphanModulesList.setItems(orphanModules, module -> FileUtilRt.getNameWithoutExtension(new File(module.getFirst()).getName())); //NON-NLS
        orphanModulesList.setBorder(JBUI.Borders.empty(5));

        JScrollPane myModulesScrollPane =
          ScrollPaneFactory.createScrollPane(orphanModulesList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                             ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        myModulesScrollPane.setBorder(new MatteBorder(0, 0, 1, 0, JBColor.border()));
        myModulesScrollPane.setMaximumSize(new Dimension(-1, 300));

        JPanel content = new JPanel(new BorderLayout());
        content.add(myModulesScrollPane, BorderLayout.CENTER);
        return content;
      }

      @Override
      protected @NotNull JComponent createNorthPanel() {
        GridBagConstraints gbConstraints = new GridBagConstraints();
        JPanel panel = new JPanel(new GridBagLayout());
        gbConstraints.insets = JBUI.insets(4, 0, 10, 8);
        panel.add(new JLabel(ExternalSystemBundle.message("orphan.modules.dialog.text")), gbConstraints);
        return panel;
      }
    };

    if (dialog.showAndGet()) {
      ExternalSystemApiUtil.doWriteAction(() -> {
        for (int i = 0; i < orphanModules.size(); i++) {
          Pair<String, Path> pair = orphanModules.get(i);
          String originalPath = pair.first;
          Path savedPath = pair.second;
          if (orphanModulesList.isItemSelected(i) && savedPath.toFile().isFile()) {
            try {
              File file = new File(originalPath);
              FileUtil.copy(savedPath.toFile(), file);
              ModuleManager.getInstance(project).loadModule(file.toPath());
            }
            catch (IOException | ModuleWithNameAlreadyExists e) {
              LOG.warn(e);
            }
          }
        }
      });
      return true;
    }
    return false;
  }

  public static void unlinkModuleFromExternalSystem(@NotNull Module module) {
    ExternalSystemModulePropertyManager.getInstance(module).unlinkExternalOptions();
  }

  protected void setModuleOptions(Module module, DataNode<E> moduleDataNode) {
    ModuleData moduleData = moduleDataNode.getData();
    module.putUserData(MODULE_DATA_KEY, moduleData);
    ExternalSystemModulePropertyManager.getInstance(module)
      .setExternalOptions(moduleData.getOwner(), moduleData, moduleDataNode.getData(ProjectKeys.PROJECT));
  }

  @Override
  public void postProcess(@NotNull Collection<? extends DataNode<E>> toImport,
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
      moduleDataNode.putUserData(MODULE_KEY, null);
      moduleDataNode.putUserData(ORDERED_DATA_MAP_KEY, null);
    }

    for (Module module : modelsProvider.getModules()) {
      module.putUserData(MODULE_DATA_KEY, null);
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
      if (order1 != order2) {
        return order1 < order2 ? -1 : 1;
      }
      return o1.second.toString().compareTo(o2.second.toString());
    });
    final List<OrderEntry> noOrderAwareItems = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      final OrderAware orderAware = orderEntryDataMap.get(orderEntry);
      if (orderAware == null) {
        noOrderAwareItems.add(orderEntry);
      }
      else {
        priorityQueue.add(Pair.create(orderEntry, orderAware));
      }
    }

    for (int i = 0; i < noOrderAwareItems.size(); i++) {
      newOrder[i] = noOrderAwareItems.get(i);
    }
    int index = noOrderAwareItems.size();
    Pair<OrderEntry, OrderAware> pair;
    while ((pair = priorityQueue.poll()) != null) {
      newOrder[index] = pair.first;
      index++;
    }

    if (LOG.isTraceEnabled()) {
      boolean changed = !Arrays.equals(orderEntries, newOrder);
      LOG.trace(String.format("rearrange status (%s): %s", modifiableRootModel.getModule(), changed ? "modified" : "not modified"));
    }
    modifiableRootModel.rearrangeOrderEntries(newOrder);
  }
}
