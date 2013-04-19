package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
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
public class ContentRootDataService implements ProjectDataService<ContentRootData> {

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

    Map<DataNode<ModuleData>, Collection<DataNode<ContentRootData>>> byModule
      = ExternalSystemUtil.groupBy(toImport, ProjectKeys.MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ContentRootData>>> entry : byModule.entrySet()) {
      final Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        LOG.warn(String.format(
          "Can't import content roots. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      importData(entry.getValue(), entry.getKey().getData().getOwner(), project, module, synchronous);
    }
  }

  private static void importData(@NotNull final Collection<DataNode<ContentRootData>> datas,
                                 @NotNull ProjectSystemId owner,
                                 @NotNull Project project,
                                 @NotNull final Module module,
                                 boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(project, owner, datas, synchronous, new Runnable() {
      @Override
      public void run() {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel model = moduleRootManager.getModifiableModel();
        try {
          for (DataNode<ContentRootData> data : datas) {
            ContentRootData contentRoot = data.getData();
            ContentEntry contentEntry = model.addContentEntry(toVfsUrl(contentRoot.getRootPath()));
            LOG.info(String.format("Importing content root '%s' for module '%s'", contentRoot.getRootPath(), module.getName()));
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.SOURCE)) {
              contentEntry.addSourceFolder(toVfsUrl(path), false);
              LOG.info(String.format(
                "Importing source root '%s' for content root '%s' of module '%s'",
                path, contentRoot.getRootPath(), module.getName()
              ));
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.TEST)) {
              contentEntry.addSourceFolder(toVfsUrl(path), true);
              LOG.info(String.format(
                "Importing test root '%s' for content root '%s' of module '%s'",
                path, contentRoot.getRootPath(), module.getName()
              ));
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.EXCLUDED)) {
              contentEntry.addExcludeFolder(toVfsUrl(path));
              LOG.info(String.format(
                "Importing excluded root '%s' for content root '%s' of module '%s'",
                path, contentRoot.getRootPath(), module.getName()
              ));
            }
          }
        }
        finally {
          model.commit();
        }
      }
    });
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<ContentRootData>> toRemove, @NotNull Project project, boolean synchronous) {
    if (toRemove.isEmpty()) {
      return;
    }
    Map<DataNode<ModuleData>,Collection<DataNode<ContentRootData>>> byModule
      = ExternalSystemUtil.groupBy(toRemove, ProjectKeys.MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ContentRootData>>> entry : byModule.entrySet()) {
      final Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        LOG.warn(String.format(
          "Can't import content roots. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      List<ModuleAwareContentRoot> contentRoots = ContainerUtilRt.newArrayList();
      for (DataNode<ContentRootData> holder : entry.getValue()) {
        ModuleAwareContentRoot contentRoot = myProjectStructureHelper.findIdeContentRoot(holder.getData().getId(holder), project);
        if (contentRoot != null) {
          contentRoots.add(contentRoot);
        }
      }
      doRemoveData(contentRoots, project, synchronous);
    }
  }

  private static void doRemoveData(@NotNull final Collection<ModuleAwareContentRoot> contentRoots,
                                   @NotNull Project project,
                                   boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, contentRoots, synchronous, new Runnable() {
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