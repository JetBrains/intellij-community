// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ContentRootData.SourceRoot;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.statistics.HasSharedSourcesUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.JpsImportedEntitySource;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import kotlin.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;

@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public final class ContentRootDataService extends AbstractProjectDataService<ContentRootData, ContentEntry> {
  public static final com.intellij.openapi.util.Key<Boolean> CREATE_EMPTY_DIRECTORIES =
    com.intellij.openapi.util.Key.create("createEmptyDirectories");

  private static final Logger LOG = Logger.getInstance(ContentRootDataService.class);

  @NotNull
  @Override
  public Key<ContentRootData> getTargetDataKey() {
    return ProjectKeys.CONTENT_ROOT;
  }

  @Override
  public void importData(@NotNull Collection<? extends DataNode<ContentRootData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    logUnitTest("Importing data. Data size is [" + toImport.size() + "]");
    if (toImport.isEmpty()) {
      return;
    }

    boolean isNewlyImportedProject = project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) == Boolean.TRUE;
    boolean forceDirectoriesCreation = false;
    DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(toImport.iterator().next(), ProjectKeys.PROJECT);
    if (projectDataNode != null) {
      forceDirectoriesCreation = projectDataNode.getUserData(CREATE_EMPTY_DIRECTORIES) == Boolean.TRUE;
    }

    Set<Module> modulesToExpand = CollectionFactory.createSmallMemoryFootprintSet();
    MultiMap<DataNode<ModuleData>, DataNode<ContentRootData>> byModule = ExternalSystemApiUtil.groupBy(toImport, ModuleData.class);

    filterAndReportDuplicatingContentRoots(byModule, project);

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
      importData(project, modelsProvider, entry.getValue(), module, forceDirectoriesCreation, projectData == null ? null : projectData.getOwner());
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
            StartupManager.getInstance(project).runAfterOpened(() -> {
              ApplicationManager.getApplication().invokeLater(() -> {
                final ProjectView projectView = ProjectView.getInstance(project);
                projectView.changeViewCB(ProjectViewPane.ID, null).doWhenProcessed(() -> projectView.selectCB(null, virtualFile, false));
              }, ModalityState.nonModal(), project.getDisposed());
            });
          }
        }
      }
    }
  }

  private static void importData(@NotNull Project project,
                                  @NotNull IdeModifiableModelsProvider modelsProvider,
                                 @NotNull final Collection<? extends DataNode<ContentRootData>> data,
                                 @NotNull final Module module, boolean forceDirectoriesCreation,
                                 @Nullable ProjectSystemId owner) {
    logUnitTest("Import data for module [" + module.getName() + "], data size [" + data.size() + "]");
    final SourceFolderManager sourceFolderManager = SourceFolderManager.getInstance(project);
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    final ContentEntry[] contentEntries = modifiableRootModel.getContentEntries();
    final Map<String, ContentEntry> contentEntriesMap = new HashMap<>();
    for (ContentEntry contentEntry : contentEntries) {
      contentEntriesMap.put(contentEntry.getUrl(), contentEntry);
    }

    sourceFolderManager.removeSourceFolders(module);

    final Set<ContentEntry> importedContentEntries = new ReferenceOpenHashSet<>();
    for (final DataNode<ContentRootData> node : data) {
      final ContentRootData contentRoot = node.getData();

      final ContentEntry contentEntry = findOrCreateContentRoot(modifiableRootModel, contentRoot);
      if (!importedContentEntries.contains(contentEntry)) {
        removeSourceFoldersIfAbsent(contentEntry, contentRoot);
        removeImportedExcludeFolders(contentEntry, modelsProvider, owner, project);
        importedContentEntries.add(contentEntry);
      }
      logDebug("Importing content root '%s' for module '%s' forceDirectoriesCreation=[%b]",
               contentRoot.getRootPath(), module.getName(), forceDirectoriesCreation);

      Set<String> updatedSourceRoots = new HashSet<>();
      for (ExternalSystemSourceType externalSrcType : ExternalSystemSourceType.values()) {
        final JpsModuleSourceRootType<?> type = getJavaSourceRootType(externalSrcType);
        if (type != null) {
          for (SourceRoot sourceRoot : contentRoot.getPaths(externalSrcType)) {
            String sourceRootPath = sourceRoot.getPath();
            boolean createSourceFolder = !updatedSourceRoots.contains(sourceRootPath);
            if (createSourceFolder) {
              createOrReplaceSourceFolder(sourceFolderManager, contentEntry, sourceRoot, module, type, forceDirectoriesCreation,
                                          ExternalSystemApiUtil.toExternalSource(contentRoot.getOwner()));
              if (externalSrcType == ExternalSystemSourceType.SOURCE || externalSrcType == ExternalSystemSourceType.TEST) {
                updatedSourceRoots.add(sourceRootPath);
              }
            }
            configureSourceFolder(sourceFolderManager, contentEntry, sourceRoot, createSourceFolder, externalSrcType.isGenerated());
          }
        }
      }

      for (SourceRoot path : contentRoot.getPaths(ExternalSystemSourceType.EXCLUDED)) {
        createExcludedRootIfAbsent(contentEntry, path, module.getName());
      }
      contentEntriesMap.remove(contentEntry.getUrl());
    }
    for (ContentEntry contentEntry : contentEntriesMap.values()) {
      modifiableRootModel.removeContentEntry(contentEntry);
    }
  }

  private static void removeImportedExcludeFolders(@NotNull ContentEntry contentEntry,
                                                   @NotNull IdeModifiableModelsProvider modelsProvider,
                                                   @Nullable ProjectSystemId owner, @NotNull Project project) {
    if (owner == null) {
      return; // can not remove imported exclude folders is source is not known
    }
    if (modelsProvider instanceof IdeModifiableModelsProviderImpl impl) {
      MutableEntityStorage diff = impl.getActualStorageBuilder();

      VirtualFileUrl vfu = project.getService(VirtualFileUrlManager.class).fromUrl(contentEntry.getUrl());
      Pair<WorkspaceEntity, String> result = ContainerUtil.find(diff.getVirtualFileUrlIndex().findEntitiesByUrl(vfu).iterator(), pair -> {
        return "url".equals(pair.component2()) && pair.component1() instanceof ContentRootEntity;
      });

      if (result != null && result.component1() instanceof ContentRootEntity contentRootEntity) {
        for (ExcludeUrlEntity excludeEntity : contentRootEntity.getExcludedUrls()) {
          if (isImportedEntity(owner, excludeEntity)) {
            diff.removeEntity(excludeEntity);
          }
        }
      }
    }
  }

  private static boolean isImportedEntity(@NotNull ProjectSystemId owner, @NotNull ExcludeUrlEntity excludeEntity) {
    return excludeEntity.getEntitySource() instanceof JpsImportedEntitySource importedEntitySource
           && owner.getId().equals(importedEntitySource.getExternalSystemId());
  }

  @Nullable
  private static JpsModuleSourceRootType<?> getJavaSourceRootType(ExternalSystemSourceType type) {
    return switch (type) {
      case SOURCE, SOURCE_GENERATED -> JavaSourceRootType.SOURCE;
      case TEST, TEST_GENERATED -> JavaSourceRootType.TEST_SOURCE;
      case EXCLUDED -> null;
      case RESOURCE, RESOURCE_GENERATED -> JavaResourceRootType.RESOURCE;
      case TEST_RESOURCE, TEST_RESOURCE_GENERATED -> JavaResourceRootType.TEST_RESOURCE;
    };
  }

  @NotNull
  private static ContentEntry findOrCreateContentRoot(@NotNull ModifiableRootModel model, @NotNull ContentRootData contentRootData) {
    String path = contentRootData.getRootPath();
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
    return model.addContentEntry(pathToUrl(path), ExternalSystemApiUtil.toExternalSource(contentRootData.getOwner()));
  }

  private static Set<String> getSourceRoots(@NotNull ContentRootData contentRoot) {
    Set<String> sourceRoots = CollectionFactory.createFilePathSet();
    for (ExternalSystemSourceType externalSrcType : ExternalSystemSourceType.values()) {
      final JpsModuleSourceRootType<?> type = getJavaSourceRootType(externalSrcType);
      if (type == null) continue;
      for (SourceRoot path : contentRoot.getPaths(externalSrcType)) {
        if (path == null) continue;
        sourceRoots.add(path.getPath());
      }
    }
    return sourceRoots;
  }

  private static void removeSourceFoldersIfAbsent(@NotNull ContentEntry contentEntry, @NotNull ContentRootData contentRoot) {
    SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
    if (sourceFolders.length == 0) return;
    Set<String> sourceRoots = getSourceRoots(contentRoot);
    for (SourceFolder sourceFolder : sourceFolders) {
      String url = sourceFolder.getUrl();
      String path = VfsUtilCore.urlToPath(url);
      if (!sourceRoots.contains(path)) {
        contentEntry.removeSourceFolder(sourceFolder);
      }
    }
  }

  private static void createOrReplaceSourceFolder(@NotNull SourceFolderManager sourceFolderManager,
                                                  @NotNull ContentEntry contentEntry,
                                                  @NotNull final SourceRoot sourceRoot,
                                                  @NotNull Module module,
                                                  @NotNull JpsModuleSourceRootType<?> sourceRootType,
                                                  boolean createEmptyContentRootDirectories,
                                                  @NotNull ProjectModelExternalSource externalSource) {
    String path = sourceRoot.getPath();
    if (SystemInfo.isWindows) {
      if (!path.isEmpty() && StringUtil.isWhiteSpace(path.charAt(path.length() - 1))) {
        LOG.warn("Source root ending with a space found. Such directories is not properly supported by JDK on Windows, see https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8190546. " +
                 "The source root will not be added: '" + path + "'");
        return;
      }
    }

    if (createEmptyContentRootDirectories) {
      createEmptyDirectory(path);
    }

    SourceFolder folder = findSourceFolder(contentEntry, sourceRoot);
    if (folder != null) {
      final JpsModuleSourceRootType<?> folderRootType = folder.getRootType();
      if (sourceRootType.equals(folderRootType)) {
        return;
      }
      contentEntry.removeSourceFolder(folder);
    }

    String url = pathToUrl(path);
    if (!Files.exists(Path.of(path))) {
      logDebug("Source folder [%s] does not exist and will not be created, will add when dir is created", url);
      logUnitTest("Adding source folder listener to watch [%s] for creation in project [hashCode=%d]", url, module.getProject().hashCode());
      sourceFolderManager.addSourceFolder(module, url, sourceRootType);
    }
    else {
      contentEntry.addSourceFolder(url, sourceRootType, externalSource);
    }
  }

  private static void configureSourceFolder(@NotNull SourceFolderManager sourceFolderManager,
                                            @NotNull ContentEntry contentEntry,
                                            @NotNull SourceRoot sourceRoot,
                                            boolean updatePackagePrefix,
                                            boolean generated) {
    String packagePrefix = sourceRoot.getPackagePrefix();
    String url = pathToUrl(sourceRoot.getPath());

    logDebug("Importing root '%s' with packagePrefix=[%s] generated=[%b]", sourceRoot, packagePrefix, generated);

    SourceFolder folder = findSourceFolder(contentEntry, sourceRoot);
    if (folder == null) {
      if (updatePackagePrefix) {
        sourceFolderManager.setSourceFolderPackagePrefix(url, packagePrefix);
      }
      if (generated) {
        sourceFolderManager.setSourceFolderGenerated(url, true);
      }
    }
    else {
      if (updatePackagePrefix && StringUtil.isNotEmpty(packagePrefix)) {
        folder.setPackagePrefix(packagePrefix);
      }
      if (generated) {
        setForGeneratedSources(folder, true);
      }
    }
  }

  private static void createEmptyDirectory(@NotNull String path) {
    if (Files.exists(Path.of(path))) return;
    ExternalSystemApiUtil.doWriteAction(() -> {
      try {
        VfsUtil.createDirectoryIfMissing(path);
      }
      catch (IOException e) {
        LOG.warn(String.format("Unable to create directory for the path: %s", path), e);
      }
    });
  }

  @Nullable
  private static SourceFolder findSourceFolder(@NotNull ContentEntry contentEntry, @NotNull SourceRoot sourceRoot) {
    for (SourceFolder folder : contentEntry.getSourceFolders()) {
      VirtualFile file = folder.getFile();
      if (file == null) continue;
      String folderPath = ExternalSystemApiUtil.getLocalFileSystemPath(file);
      String rootPath = sourceRoot.getPath();
      if (folderPath.equals(rootPath)) return folder;
    }
    return null;
  }

  private static void setForGeneratedSources(@NotNull SourceFolder folder, boolean generated) {
    JpsModuleSourceRoot jpsElement = folder.getJpsElement();
    JavaSourceRootProperties properties = jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES);
    if (properties != null) properties.setForGeneratedSources(generated);
  }

  private static void logUnitTest(@NotNull String format, Object... args) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.info(String.format(format, args));
    }
  }

  private static void logDebug(@NotNull String format, Object... args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format(format, args));
    }
  }

  private static void createExcludedRootIfAbsent(@NotNull ContentEntry entry,
                                                 @NotNull SourceRoot root,
                                                 @NotNull String moduleName) {
    String rootPath = root.getPath();
    for (VirtualFile file : entry.getExcludeFolderFiles()) {
      if (ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(rootPath)) {
        return;
      }
    }
    logDebug("Importing excluded root '%s' for content root '%s' of module '%s'", root, entry.getUrl(), moduleName);
    entry.addExcludeFolder(pathToUrl(rootPath), true);
  }


  private static void filterAndReportDuplicatingContentRoots(@NotNull MultiMap<DataNode<ModuleData>, DataNode<ContentRootData>> moduleNodeToRootNodes,
                                                             @NotNull Project project) {
    Map<String, DuplicateModuleReport> filter = new LinkedHashMap<>();

    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ContentRootData>>> entry : moduleNodeToRootNodes.entrySet()) {
      ModuleData moduleData = entry.getKey().getData();
      Collection<DataNode<ContentRootData>> crDataNodes = entry.getValue();
      for (Iterator<DataNode<ContentRootData>> iterator = crDataNodes.iterator(); iterator.hasNext(); ) {
        DataNode<ContentRootData> crDataNode = iterator.next();
        String rootPath = crDataNode.getData().getRootPath();
        DuplicateModuleReport report = filter.putIfAbsent(rootPath, new DuplicateModuleReport(moduleData));
        if (report != null) {
          report.addDuplicate(moduleData);
          iterator.remove();
          crDataNode.clear(true);
        }
      }
    }

    Map<String, DuplicateModuleReport> toReport = filter.entrySet().stream()
      .filter(e -> e.getValue().hasDuplicates())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (r1, r2) -> {
        LOG.warn("Unexpected duplicates in keys while collecting filtered reports");
        return r2;
      }, LinkedHashMap::new));

    boolean hasDuplicates = !toReport.isEmpty();
    HasSharedSourcesUtil.setHasSharedSources(project, hasDuplicates);
    if (hasDuplicates) {
      String notificationMessage = prepareMessageAndLogWarnings(toReport);
      if (notificationMessage != null) {
        showNotificationsPopup(project, toReport.size(), notificationMessage);
      }
    }
  }

  @Nullable
  private static @Nls String prepareMessageAndLogWarnings(@NotNull Map<String, DuplicateModuleReport> toReport) {
    String firstMessage = null;
    LOG.warn("Duplicating content roots detected.");
    for (Map.Entry<String, DuplicateModuleReport> entry : toReport.entrySet()) {
      String path = entry.getKey();
      DuplicateModuleReport report = entry.getValue();
      String message = ExternalSystemBundle.message("duplicate.content.roots.removed", path, report.getOriginalName(),
                                                    StringUtil.join(report.getDuplicatesNames(), ", "));
      if (firstMessage == null) {
        firstMessage = message;
      }
      LOG.warn(message);
    }
    return firstMessage;
  }

  private static void showNotificationsPopup(@NotNull Project project,
                                             int reportsCount,
                                             @NotNull @Nls String notificationMessage) {
    int extraReportsCount = reportsCount - 1;
    if (extraReportsCount > 0) {
      notificationMessage += ExternalSystemBundle.message("duplicate.content.roots.extra", extraReportsCount);
    }

    Notification notification = new Notification("Content root duplicates",
                                                 ExternalSystemBundle.message("duplicate.content.roots.detected"),
                                                 notificationMessage,
                                                 NotificationType.WARNING);
    Notifications.Bus.notify(notification, project);
  }


  private static final class DuplicateModuleReport {
    private final ModuleData myOriginal;
    private final List<ModuleData> myDuplicates = new ArrayList<>();

    private DuplicateModuleReport(@NotNull ModuleData original) {
      myOriginal = original;
    }

    public void addDuplicate(@NotNull ModuleData duplicate) {
      myDuplicates.add(duplicate);
    }

    public boolean hasDuplicates() {
      return !myDuplicates.isEmpty();
    }

    public String getOriginalName() {
      return myOriginal.getInternalName();
    }

    public Collection<String> getDuplicatesNames() {
      return ContainerUtil.map(myDuplicates, ModuleData::getInternalName);
    }
  }
}
