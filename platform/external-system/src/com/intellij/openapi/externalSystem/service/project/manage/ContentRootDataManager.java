package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataHolder;
import com.intellij.openapi.externalSystem.model.ExternalSystemProjectKeys;
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
public class ContentRootDataManager implements ProjectDataManager<ContentRootData> {

  private static final Logger LOG = Logger.getInstance("#" + ContentRootDataManager.class.getName());

  @NotNull private final ProjectStructureHelper myProjectStructureHelper;

  public ContentRootDataManager(@NotNull ProjectStructureHelper helper) {
    myProjectStructureHelper = helper;
  }

  @NotNull
  @Override
  public Key<ContentRootData> getTargetDataKey() {
    return ExternalSystemProjectKeys.CONTENT_ROOT;
  }

  @Override
  public void importData(@NotNull final Collection<DataHolder<ContentRootData>> datas,
                         @NotNull final Project project,
                         boolean synchronous)
  {
    if (datas.isEmpty()) {
      return;
    }

    Map<ModuleData,Collection<DataHolder<ContentRootData>>> byModule = ExternalSystemUtil.groupBy(datas, ExternalSystemProjectKeys.MODULE);
    for (Map.Entry<ModuleData, Collection<DataHolder<ContentRootData>>> entry : byModule.entrySet()) {
      final Module module = myProjectStructureHelper.findIdeModule(entry.getKey(), project);
      if (module == null) {
        LOG.warn(String.format(
          "Can't import content roots. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      importData(entry.getValue(), entry.getKey().getOwner(), project, module, synchronous);
    }
  }

  private static void importData(@NotNull final Collection<DataHolder<ContentRootData>> datas,
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
          for (DataHolder<ContentRootData> data : datas) {
            ContentRootData contentRoot = data.getData();
            ContentEntry contentEntry = model.addContentEntry(toVfsUrl(contentRoot.getRootPath()));
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.SOURCE)) {
              contentEntry.addSourceFolder(toVfsUrl(path), false);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.TEST)) {
              contentEntry.addSourceFolder(toVfsUrl(path), true);
            }
            for (String path : contentRoot.getPaths(ExternalSystemSourceType.EXCLUDED)) {
              contentEntry.addExcludeFolder(toVfsUrl(path));
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
  public void removeData(@NotNull Collection<DataHolder<ContentRootData>> datas, @NotNull Project project, boolean synchronous) {
    if (datas.isEmpty()) {
      return;
    }
    Map<ModuleData,Collection<DataHolder<ContentRootData>>> byModule = ExternalSystemUtil.groupBy(datas, ExternalSystemProjectKeys.MODULE);
    for (Map.Entry<ModuleData, Collection<DataHolder<ContentRootData>>> entry : byModule.entrySet()) {
      final Module module = myProjectStructureHelper.findIdeModule(entry.getKey(), project);
      if (module == null) {
        LOG.warn(String.format(
          "Can't import content roots. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      List<ModuleAwareContentRoot> contentRoots = ContainerUtilRt.newArrayList();
      for (DataHolder<ContentRootData> holder : entry.getValue()) {
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