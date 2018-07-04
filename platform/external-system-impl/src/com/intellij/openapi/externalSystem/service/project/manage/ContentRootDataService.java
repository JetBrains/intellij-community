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

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ContentRootData.SourceRoot;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 3:20 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ContentRootDataService extends AbstractProjectDataService<ContentRootData, ContentEntry> {
  public static final com.intellij.openapi.util.Key<Boolean> CREATE_EMPTY_DIRECTORIES =
    com.intellij.openapi.util.Key.create("createEmptyDirectories");
  private static final com.intellij.openapi.util.Key<Set<AddSourceFolderListener>> LISTENERS_KEY =
    com.intellij.openapi.util.Key.create("postponedSourceFolderCreationListeners");

  private static final Logger LOG = Logger.getInstance(ContentRootDataService.class);

  @NotNull
  @Override
  public Key<ContentRootData> getTargetDataKey() {
    return ProjectKeys.CONTENT_ROOT;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ContentRootData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      return;
    }

    boolean isNewlyImportedProject = project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) == Boolean.TRUE;
    boolean forceDirectoriesCreation = false;
    DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(toImport.iterator().next(), ProjectKeys.PROJECT);
    if (projectDataNode != null) {
      forceDirectoriesCreation = projectDataNode.getUserData(CREATE_EMPTY_DIRECTORIES) == Boolean.TRUE;
    }

    Set<Module> modulesToExpand = ContainerUtil.newTroveSet();
    MultiMap<DataNode<ModuleData>, DataNode<ContentRootData>> byModule = ExternalSystemApiUtil.groupBy(toImport, ModuleData.class);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ContentRootData>>> entry : byModule.entrySet()) {
      Module module = entry.getKey().getUserData(AbstractModuleDataService.MODULE_KEY);
      module = module != null ? module : modelsProvider.findIdeModule(entry.getKey().getData());
      if (module == null) {
        LOG.warn(String.format(
          "Can't import content roots. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      importData(modelsProvider, entry.getValue(), module, forceDirectoriesCreation);
      if (forceDirectoriesCreation ||
          (isNewlyImportedProject &&
           projectData != null &&
           projectData.getLinkedExternalProjectPath().equals(ExternalSystemApiUtil.getExternalProjectPath(module)))) {
        modulesToExpand.add(module);
      }
    }
    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !modulesToExpand.isEmpty()) {
      for (Module module : modulesToExpand) {
        String productionModuleName = modelsProvider.getProductionModuleName(module);
        if (productionModuleName == null || !modulesToExpand.contains(modelsProvider.findIdeModule(productionModuleName))) {
          VirtualFile[] roots = modelsProvider.getModifiableRootModel(module).getContentRoots();
          if (roots.length > 0) {
            VirtualFile virtualFile = roots[0];
            ExternalSystemUtil.invokeLater(project, ModalityState.NON_MODAL, () -> {
              final ProjectView projectView = ProjectView.getInstance(project);
              projectView.changeViewCB(ProjectViewPane.ID, null).doWhenProcessed(() -> projectView.selectCB(null, virtualFile, false));
            });
          }
        }
      }
    }
  }

  private static void importData(@NotNull IdeModifiableModelsProvider modelsProvider,
                                 @NotNull final Collection<DataNode<ContentRootData>> data,
                                 @NotNull final Module module, boolean forceDirectoriesCreation) {
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    final ContentEntry[] contentEntries = modifiableRootModel.getContentEntries();
    final Map<String, ContentEntry> contentEntriesMap = ContainerUtilRt.newHashMap();
    for (ContentEntry contentEntry : contentEntries) {
      contentEntriesMap.put(contentEntry.getUrl(), contentEntry);
    }

    boolean createEmptyContentRootDirectories = forceDirectoriesCreation;
    if (!forceDirectoriesCreation && !data.isEmpty()) {
      ProjectSystemId projectSystemId = data.iterator().next().getData().getOwner();
      AbstractExternalSystemSettings externalSystemSettings =
        ExternalSystemApiUtil.getSettings(module.getProject(), projectSystemId);

      String path = ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath();
      if (path != null) {
        ExternalProjectSettings projectSettings = externalSystemSettings.getLinkedProjectSettings(path);
        createEmptyContentRootDirectories = projectSettings != null && projectSettings.isCreateEmptyContentRootDirectories();
      }
    }

    cleanPostponedSourceFolderCreationListeners(module);

    final Set<ContentEntry> importedContentEntries = ContainerUtil.newIdentityTroveSet();
    for (final DataNode<ContentRootData> node : data) {
      final ContentRootData contentRoot = node.getData();

      final ContentEntry contentEntry = findOrCreateContentRoot(modifiableRootModel, contentRoot.getRootPath());
      if(!importedContentEntries.contains(contentEntry)) {
        // clear source folders but do not remove existing excluded folders
        contentEntry.clearSourceFolders();
        importedContentEntries.add(contentEntry);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Importing content root '%s' for module '%s'", contentRoot.getRootPath(), module.getName()));
      }

      for (ExternalSystemSourceType externalSrcType : ExternalSystemSourceType.values()) {
        final JpsModuleSourceRootType<?> type = getJavaSourceRootType(externalSrcType);
        if (type != null) {
          for (SourceRoot path : contentRoot.getPaths(externalSrcType)) {
            createSourceRootIfAbsent(
              contentEntry, path, module, type, externalSrcType.isGenerated(), createEmptyContentRootDirectories);
          }
        }
      }

      for (SourceRoot path : contentRoot.getPaths(ExternalSystemSourceType.EXCLUDED)) {
        createExcludedRootIfAbsent(contentEntry, path, module.getName(), module.getProject());
      }
      contentEntriesMap.remove(contentEntry.getUrl());
    }
    for (ContentEntry contentEntry : contentEntriesMap.values()) {
      modifiableRootModel.removeContentEntry(contentEntry);
    }
  }

  private static void cleanPostponedSourceFolderCreationListeners(@NotNull Module module) {
    final Set<AddSourceFolderListener> listeners = module.getUserData(LISTENERS_KEY);
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    if (listeners != null) {
      for (AddSourceFolderListener listener : listeners) {
        vfManager.removeVirtualFileListener(listener);
      }
      listeners.clear();
    }
  }

  private static void saveSourceFolderCreationListener(@NotNull Module module, @NotNull AddSourceFolderListener listener) {
    Set<AddSourceFolderListener> listeners = module.getUserData(LISTENERS_KEY);
    if (listeners == null) {
      listeners = new HashSet<>();
      module.putUserData(LISTENERS_KEY, listeners);
    }
    listeners.add(listener);
  }

  @Nullable
  private static JpsModuleSourceRootType<?> getJavaSourceRootType(ExternalSystemSourceType type) {
    switch (type) {
      case SOURCE:
        return JavaSourceRootType.SOURCE;
      case TEST:
        return JavaSourceRootType.TEST_SOURCE;
      case EXCLUDED:
        return null;
      case SOURCE_GENERATED:
        return JavaSourceRootType.SOURCE;
      case TEST_GENERATED:
        return JavaSourceRootType.TEST_SOURCE;
      case RESOURCE:
        return JavaResourceRootType.RESOURCE;
      case TEST_RESOURCE:
        return JavaResourceRootType.TEST_RESOURCE;
    }
    return null;
  }

  @NotNull
  private static ContentEntry findOrCreateContentRoot(@NotNull ModifiableRootModel model, @NotNull String path) {
    ContentEntry[] entries = model.getContentEntries();

    for (ContentEntry entry : entries) {
      VirtualFile file = entry.getFile();
      if (file == null) {
        continue;
      }
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return entry;
      }
    }
    return model.addContentEntry(pathToUrl(path));
  }

  private static void createSourceRootIfAbsent(
    @NotNull ContentEntry entry, @NotNull final SourceRoot root, @NotNull Module module,
    @NotNull JpsModuleSourceRootType<?> sourceRootType, boolean generated, boolean createEmptyContentRootDirectories) {
    SourceFolder[] folders = entry.getSourceFolders();
    for (SourceFolder folder : folders) {
      VirtualFile file = folder.getFile();
      if (file == null) {
        continue;
      }
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(root.getPath())) {
        final JpsModuleSourceRootType<?> folderRootType = folder.getRootType();
        if(JavaSourceRootType.SOURCE.equals(folderRootType) || sourceRootType.equals(folderRootType)) {
          return;
        }
        if(JavaSourceRootType.TEST_SOURCE.equals(folderRootType) && JavaResourceRootType.TEST_RESOURCE.equals(sourceRootType)) {
          return;
        }
        entry.removeSourceFolder(folder);
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Importing %s for content root '%s' of module '%s'", root, entry.getUrl(), module.getName()));
    }

    if (!createEmptyContentRootDirectories && !generated) {
      final Ref<VirtualFile> ref = Ref.create();
      ExternalSystemApiUtil.doWriteAction(() -> ref.set(LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getPath())));
      final VirtualFile sourceFolderFile = ref.get();
      if (sourceFolderFile == null || !sourceFolderFile.isValid()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Source folder [" + root.getPath() + "] does not exist and will not be created, will add when dir is created");
        }
        final AddSourceFolderListener listener = new AddSourceFolderListener(root, module, sourceRootType);
        saveSourceFolderCreationListener(module, listener);
        VirtualFileManager.getInstance().addVirtualFileListener(listener, module);
        return;
      }
    }

    SourceFolder sourceFolder = entry.addSourceFolder(pathToUrl(root.getPath()), sourceRootType);
    if (!StringUtil.isEmpty(root.getPackagePrefix())) {
      sourceFolder.setPackagePrefix(root.getPackagePrefix());
    }
    if (generated) {
      JavaSourceRootProperties properties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
      if(properties != null) {
        properties.setForGeneratedSources(true);
      }
    }
    if(createEmptyContentRootDirectories) {
      ExternalSystemApiUtil.doWriteAction(() -> {
        try {
          VfsUtil.createDirectoryIfMissing(root.getPath());
        }
        catch (IOException e) {
          LOG.warn(String.format("Unable to create directory for the path: %s", root.getPath()), e);
        }
      });
    }
  }

  private static void createExcludedRootIfAbsent(@NotNull ContentEntry entry, @NotNull SourceRoot root, @NotNull String moduleName, @NotNull Project project) {
    String rootPath = root.getPath();
    for (VirtualFile file : entry.getExcludeFolderFiles()) {
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(rootPath)) {
        return;
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Importing excluded root '%s' for content root '%s' of module '%s'", root, entry.getUrl(), moduleName));
    }
    entry.addExcludeFolder(pathToUrl(rootPath));
    if (!Registry.is("ide.hide.excluded.files")) {
      ChangeListManager.getInstance(project).addDirectoryToIgnoreImplicitly(rootPath);
    }
  }
}