package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2/7/12 3:20 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ContentRootDataService implements ProjectDataService<ContentRootData, ContentEntry> {

  private static final Logger LOG = Logger.getInstance("#" + ContentRootDataService.class.getName());

  @NotNull private final ProjectStructureHelper myProjectStructureHelper;

  public ContentRootDataService(@NotNull ProjectStructureHelper helper) {
    myProjectStructureHelper = helper;
  }

  @NotNull
  @Override
  public Key<ContentRootData> getTargetDataKey() {
    return ProjectKeys.CONTENT_ROOT;
  }

  @Override
  public void importData(@NotNull final Collection<DataNode<ContentRootData>> toImport,
                         @NotNull final Project project,
                         boolean synchronous)
  {
    if (toImport.isEmpty()) {
      return;
    }

    Map<DataNode<ModuleData>, List<DataNode<ContentRootData>>> byModule = ExternalSystemApiUtil.groupBy(toImport, ProjectKeys.MODULE);
    for (Map.Entry<DataNode<ModuleData>, List<DataNode<ContentRootData>>> entry : byModule.entrySet()) {
      final Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        LOG.warn(String.format(
          "Can't import content roots. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      importData(entry.getValue(), module, synchronous);
    }
  }

  private static void importData(@NotNull final Collection<DataNode<ContentRootData>> datas,
                                 @NotNull final Module module,
                                 boolean synchronous)
  {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(module) {
      @Override
      public void execute() {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel model = moduleRootManager.getModifiableModel();
        final ContentEntry[] contentEntries = model.getContentEntries();
        final Map<String, ContentEntry> contentEntriesMap = ContainerUtilRt.newHashMap();
        for(ContentEntry contentEntry : contentEntries) {
          contentEntriesMap.put(contentEntry.getUrl(), contentEntry);
        }

        boolean createEmptyContentRootDirectories = false;
        if (!datas.isEmpty()) {
          ProjectSystemId projectSystemId = datas.iterator().next().getData().getOwner();
          AbstractExternalSystemSettings externalSystemSettings =
            ExternalSystemApiUtil.getSettings(module.getProject(), projectSystemId);

          String path = module.getOptionValue(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
          if (path != null) {
            ExternalProjectSettings projectSettings = externalSystemSettings.getLinkedProjectSettings(path);
            createEmptyContentRootDirectories = projectSettings != null && projectSettings.isCreateEmptyContentRootDirectories();
          }
        }

        try {
          for (final DataNode<ContentRootData> data : datas) {
            final ContentRootData contentRoot = data.getData();

            final ContentEntry contentEntry = findOrCreateContentRoot(model, contentRoot.getRootPath());
            contentEntry.clearExcludeFolders();
            contentEntry.clearSourceFolders();
            LOG.info(String.format("Importing content root '%s' for module '%s'", contentRoot.getRootPath(), module.getName()));
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.SOURCE)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName(), JavaSourceRootType.SOURCE, false, createEmptyContentRootDirectories);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.TEST)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName(), JavaSourceRootType.TEST_SOURCE, false, createEmptyContentRootDirectories);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.RESOURCE)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName(), JavaResourceRootType.RESOURCE, false, createEmptyContentRootDirectories);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.TEST_RESOURCE)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName(), JavaResourceRootType.TEST_RESOURCE, false, createEmptyContentRootDirectories);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.SOURCE_GENERATED)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName(), JavaSourceRootType.SOURCE, true, createEmptyContentRootDirectories);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.TEST_GENERATED)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName(), JavaSourceRootType.TEST_SOURCE, true, createEmptyContentRootDirectories);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.EXCLUDED)) {
              createExcludedRootIfAbsent(contentEntry, path, module.getName());
            }
            contentEntriesMap.remove(contentEntry.getUrl());
          }
          for(ContentEntry contentEntry : contentEntriesMap.values()) {
            model.removeContentEntry(contentEntry);
          }
        }
        finally {
          model.commit();
        }
      }
    });
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
    return model.addContentEntry(toVfsUrl(path));
  }

  private static void createSourceRootIfAbsent(
    @NotNull ContentEntry entry, @NotNull String path, @NotNull String moduleName,
    @NotNull JpsModuleSourceRootType sourceRootType, boolean generated, boolean createEmptyContentRootDirectories) {
    List<SourceFolder> folders = entry.getSourceFolders(sourceRootType);
    for (SourceFolder folder : folders) {
      VirtualFile file = folder.getFile();
      if (file == null) {
        continue;
      }
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return;
      }
    }
    LOG.info(String.format("Importing source root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
    SourceFolder sourceFolder = entry.addSourceFolder(toVfsUrl(path), sourceRootType);
    if (generated) {
      JavaSourceRootProperties properties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
      if(properties != null) {
        properties.setForGeneratedSources(true);
      }
    }
    if(createEmptyContentRootDirectories) {
      try {
        VfsUtil.createDirectoryIfMissing(path);
      }
      catch (IOException e) {
        LOG.warn(String.format("Unable to create directory for the path: %s", path), e);
      }
    }
  }

  private static void createExcludedRootIfAbsent(@NotNull ContentEntry entry, @NotNull String path, @NotNull String moduleName) {
    for (VirtualFile file : entry.getExcludeFolderFiles()) {
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return;
      }
    }
    LOG.info(String.format("Importing excluded root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
    entry.addExcludeFolder(toVfsUrl(path));
  }

  @Override
  public void removeData(@NotNull Collection<? extends ContentEntry> toRemove, @NotNull Project project, boolean synchronous) {
  }

  private static String toVfsUrl(@NotNull String path) {
    return LocalFileSystem.PROTOCOL_PREFIX + path;
  }
}