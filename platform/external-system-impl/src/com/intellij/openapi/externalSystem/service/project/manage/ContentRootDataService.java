package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2/7/12 3:20 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ContentRootDataService implements ProjectDataService<ContentRootData, ModuleAwareContentRoot> {

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
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel model = moduleRootManager.getModifiableModel();
        try {
          for (DataNode<ContentRootData> data : datas) {
            ContentRootData contentRoot = data.getData();
            ContentEntry contentEntry = findOrCreateContentRoot(model, contentRoot.getRootPath());
            LOG.info(String.format("Importing content root '%s' for module '%s'", contentRoot.getRootPath(), module.getName()));
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.SOURCE)) {
              createSourceRootIfAbsent(contentEntry, path, module.getName());
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.TEST)) {
              createTestRootIfAbsent(contentEntry, path, module.getName());
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.EXCLUDED)) {
              createExcludedRootIfAbsent(contentEntry, path, module.getName());
            }
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
    if (entries == null) {
      return model.addContentEntry(toVfsUrl(path));
    }
    
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

  private static void createSourceRootIfAbsent(@NotNull ContentEntry entry, @NotNull String path, @NotNull String moduleName) {
    SourceFolder[] folders = entry.getSourceFolders();
    if (folders == null) {
      LOG.info(String.format("Importing source root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
      entry.addSourceFolder(toVfsUrl(path), false);
      return;
    }
    for (SourceFolder folder : folders) {
      if (folder.isTestSource()) {
        continue;
      }
      VirtualFile file = folder.getFile();
      if (file == null) {
        continue;
      }
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return;
      }
    }
    LOG.info(String.format("Importing source root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
    entry.addSourceFolder(toVfsUrl(path), false);
  }

  private static void createExcludedRootIfAbsent(@NotNull ContentEntry entry, @NotNull String path, @NotNull String moduleName) {
    ExcludeFolder[] folders = entry.getExcludeFolders();
    if (folders == null) {
      LOG.info(String.format("Importing excluded root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
      entry.addExcludeFolder(toVfsUrl(path));
      return;
    }
    for (ExcludeFolder folder : folders) {
      VirtualFile file = folder.getFile();
      if (file == null) {
        continue;
      }
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return;
      }
    }
    LOG.info(String.format("Importing excluded root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
    entry.addExcludeFolder(toVfsUrl(path));
  }

  private static void createTestRootIfAbsent(@NotNull ContentEntry entry, @NotNull String path, @NotNull String moduleName) {
    SourceFolder[] folders = entry.getSourceFolders();
    if (folders == null) {
      LOG.info(String.format("Importing test root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
      entry.addSourceFolder(toVfsUrl(path), true);
      return;
    }
    for (SourceFolder folder : folders) {
      if (!folder.isTestSource()) {
        continue;
      }
      VirtualFile file = folder.getFile();
      if (file == null) {
        continue;
      }
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return;
      }
    }
    LOG.info(String.format("Importing test root '%s' for content root '%s' of module '%s'", path, entry.getUrl(), moduleName));
    entry.addSourceFolder(toVfsUrl(path), true);
  }

  @Override
  public void removeData(@NotNull Collection<? extends ModuleAwareContentRoot> toRemove, @NotNull Project project, boolean synchronous) {
    Map<Module, Collection<ModuleAwareContentRoot>> byModule = ContainerUtilRt.newHashMap();
    for (ModuleAwareContentRoot root : toRemove) {
      Collection<ModuleAwareContentRoot> roots = byModule.get(root.getModule());
      if (roots == null) {
        byModule.put(root.getModule(), roots = ContainerUtilRt.newArrayList());
      }
      roots.add(root);
    }
    for (Map.Entry<Module, Collection<ModuleAwareContentRoot>> entry : byModule.entrySet()) {
      doRemoveData(entry.getValue(), synchronous);
    }
  }

  private static void doRemoveData(@NotNull final Collection<ModuleAwareContentRoot> contentRoots, boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        for (ModuleAwareContentRoot contentRoot : contentRoots) {
          final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(contentRoot.getModule());
          ModifiableRootModel model = moduleRootManager.getModifiableModel();
          try {
            model.removeContentEntry(contentRoot);
            LOG.info(String.format("Removing content root '%s' from module %s", contentRoot.getUrl(), contentRoot.getModule().getName()));
          }
          finally {
            model.commit();
          }
        }
      }
    });
  }

  private static String toVfsUrl(@NotNull String path) {
    return LocalFileSystem.PROTOCOL_PREFIX + path;
  }
}