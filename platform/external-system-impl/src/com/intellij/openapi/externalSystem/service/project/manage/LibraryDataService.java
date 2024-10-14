// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@ApiStatus.Internal
@Order(ExternalSystemConstants.BUILTIN_LIBRARY_DATA_SERVICE_ORDER)
public final class LibraryDataService extends AbstractProjectDataService<LibraryData, Library> {

  private static final Logger LOG = Logger.getInstance(LibraryDataService.class);

  public static final ExtensionPointName<LibraryDataServiceExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.libraryDataServiceExtension");

  @Override
  public @NotNull Key<LibraryData> getTargetDataKey() {
    return ProjectKeys.LIBRARY;
  }

  @Override
  public void importData(final @NotNull Collection<? extends DataNode<LibraryData>> toImport,
                         final @Nullable ProjectData projectData,
                         final @NotNull Project project,
                         final @NotNull IdeModifiableModelsProvider modelsProvider) {
    Map<String, LibraryData> processedLibraries = new HashMap<>();
    for (DataNode<LibraryData> dataNode: toImport) {
      LibraryData libraryData = dataNode.getData();
      String libraryName = libraryData.getInternalName();
      LibraryData importedLibrary = processedLibraries.putIfAbsent(libraryName, libraryData);
      if (importedLibrary == null) {
        importLibrary(libraryData, modelsProvider);
      }
      else {
        LOG.warn("Multiple project level libraries found with the same name '" + libraryName + "'");
        if (LOG.isDebugEnabled()) {
          LOG.debug("Chosen library:" + importedLibrary.getPaths(LibraryPathType.BINARY));
          LOG.debug("Ignored library:" + libraryData.getPaths(LibraryPathType.BINARY));
        }
      }
    }
  }

  private static void importLibrary(final @NotNull LibraryData toImport, final @NotNull IdeModifiableModelsProvider modelsProvider) {
    final String libraryName = toImport.getInternalName();
    Library library = modelsProvider.getLibraryByName(libraryName);
    ProjectModelExternalSource source = ExternalSystemApiUtil.toExternalSource(toImport.getOwner());
    if (library != null) {
      if (library.getExternalSource() == null) {
        ((LibraryEx.ModifiableModelEx)modelsProvider.getModifiableLibraryModel(library)).setExternalSource(source);
      }
      syncPaths(toImport, library, modelsProvider);
      return;
    }
    LibraryTable.ModifiableModel librariesModel = modelsProvider.getModifiableProjectLibrariesModel();
    library = librariesModel.createLibrary(libraryName, getLibraryKind(toImport), source);
    Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    prepareNewLibrary(toImport, libraryName, libraryModel);
  }

  private static void prepareNewLibrary(@NotNull LibraryData libraryData,
                                 @NotNull String libraryName,
                                 @NotNull Library.ModifiableModel libraryModel) {
    Map<OrderRootType, Collection<File>> libraryFiles = prepareLibraryFiles(libraryData);
    Set<String> excludedPaths = libraryData.getPaths(LibraryPathType.EXCLUDED);
    registerPaths(libraryData.isUnresolved(), libraryFiles, excludedPaths, libraryModel, libraryName);
    EP_NAME.forEachExtensionSafe(extension -> extension.prepareNewLibrary(libraryData, libraryModel));
  }

  private static PersistentLibraryKind<?> getLibraryKind(LibraryData anImport) {
    for (LibraryDataServiceExtension extension : EP_NAME.getExtensionList()) {
      PersistentLibraryKind<?> kind = extension.getLibraryKind(anImport);
      if (kind != null) {
        return kind;
      }
    }
    return null;
  }

  static @NotNull Map<OrderRootType, Collection<File>> prepareLibraryFiles(@NotNull LibraryData data) {
    Map<OrderRootType, Collection<File>> result = new HashMap<>();
    for (LibraryPathType pathType: LibraryPathType.values()) {
      OrderRootType orderRootType = ExternalLibraryPathTypeMapper.getInstance().map(pathType);
      if (orderRootType == null) {
        continue;
      }
      Set<String> paths = data.getPaths(pathType);
      if (paths.isEmpty()) {
        continue;
      }
      List<File> files = ContainerUtil.map(paths, File::new);
      result.put(orderRootType, files);
    }
    return result;
  }

  static void registerPaths(boolean unresolved,
                            @NotNull Map<OrderRootType, Collection<File>> libraryFiles,
                            @NotNull Set<String> excludedPaths,
                            @NotNull Library.ModifiableModel model,
                            @NotNull String libraryName) {
    for (Map.Entry<OrderRootType, Collection<File>> entry: libraryFiles.entrySet()) {
      for (File file: entry.getValue()) {
        VirtualFile virtualFile = unresolved ? null : VirtualFileManager.getInstance().findFileByNioPath(file.toPath().toAbsolutePath());
        if (virtualFile == null) {
          if (!unresolved && ExternalSystemConstants.VERBOSE_PROCESSING && entry.getKey() == OrderRootType.CLASSES) {
            LOG.warn(
              String.format("Can't find %s of the library '%s' at path '%s'", entry.getKey(), libraryName, file.getAbsolutePath())
            );
          }
          String url = VfsUtil.getUrlForLibraryRoot(file);

          String[] urls = model.getUrls(entry.getKey());
          if (!ArrayUtil.contains(url, urls)) {
            model.addRoot(url, entry.getKey());
          }
          continue;
        }
        if (virtualFile.isDirectory()) {
          VirtualFile[] files = model.getFiles(entry.getKey());
          if (!ArrayUtil.contains(virtualFile, files)) {
            model.addRoot(virtualFile, entry.getKey());
          }
        }
        else {
          VirtualFile root = virtualFile;
          if (virtualFile.getFileType() instanceof ArchiveFileType) {
            root = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
            if (root == null) {
              LOG.warn(String.format(
                "Can't parse contents of the JAR file at path '%s' for the library '%s''", file.getAbsolutePath(), libraryName
              ));
              continue;
            }
          }
          VirtualFile[] files = model.getFiles(entry.getKey());
          if (!ArrayUtil.contains(root, files)) {
            model.addRoot(root, entry.getKey());
          }
        }
      }
    }

    if (model instanceof LibraryEx.ModifiableModelEx modelEx) {
      for (String excludedPath : excludedPaths) {
        String url = VfsUtil.getUrlForLibraryRoot(new File(excludedPath));
        String[] urls = modelEx.getExcludedRootUrls();
        if (!ArrayUtil.contains(url, urls)) {
          modelEx.addExcludedRoot(url);
        }
      }
    }
  }

  /**
   * Remove orphan project libraries during postprocess phase (after execution of LibraryDependencyDataService#import)
   */
  @Override
  public void postProcess(@NotNull Collection<? extends DataNode<LibraryData>> toImport,
                          @Nullable ProjectData projectData,
                          @NotNull Project project,
                          @NotNull IdeModifiableModelsProvider modelsProvider) {

    if (projectData == null) return;

    // do not cleanup orphan project libraries if import runs from Project Structure Dialog
    // since libraries order entries cannot be imported for modules in that case
    // and hence orphans will be detected incorrectly
    if (modelsProvider instanceof ProjectStructureUIModifiableModelsProvider) return;

    final List<Library> orphanIdeLibraries = new SmartList<>();
    final LibraryTable.ModifiableModel librariesModel = modelsProvider.getModifiableProjectLibrariesModel();
    final Map<String, Library> namesToLibs = new HashMap<>();
    final Set<Library> potentialOrphans = new HashSet<>();
    RootPolicy<Void> excludeUsedLibraries = new RootPolicy<>() {
      @Override
      public Void visitLibraryOrderEntry(@NotNull LibraryOrderEntry ideDependency, Void value) {
        if (ideDependency.isModuleLevel()) {
          return null;
        }
        Library lib = ideDependency.getLibrary();
        if (lib == null) {
          lib = namesToLibs.get(ideDependency.getLibraryName());
        }
        if (lib != null) {
          potentialOrphans.remove(lib);
        }
        return null;
      }
    };

    for (Library library: librariesModel.getLibraries()) {
      if (!ExternalSystemApiUtil.isExternalSystemLibrary(library, projectData.getOwner())) continue;
      namesToLibs.put(library.getName(), library);
      potentialOrphans.add(library);
    }

    for (Module module: modelsProvider.getModules()) {
      for (OrderEntry entry: modelsProvider.getOrderEntries(module)) {
        entry.accept(excludeUsedLibraries, null);
      }
    }

    for (Library lib: potentialOrphans) {
      if (!modelsProvider.isSubstituted(lib.getName())) {
        orphanIdeLibraries.add(lib);
      }
    }

    for (Library library: orphanIdeLibraries) {
      String libraryName = library.getName();
      if (libraryName != null) {
        Library libraryToRemove = librariesModel.getLibraryByName(libraryName);
        if (libraryToRemove != null) {
          librariesModel.removeLibrary(libraryToRemove);
        }
      }
    }
  }

  private static void syncPaths(final @NotNull LibraryData externalLibrary,
                                final @NotNull Library ideLibrary,
                                final @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (externalLibrary.isUnresolved()) {
      return;
    }
    final Map<OrderRootType, Set<String>> toRemove = new HashMap<>();
    final Map<OrderRootType, Set<String>> toAdd = new HashMap<>();
    ExternalLibraryPathTypeMapper externalLibraryPathTypeMapper = ExternalLibraryPathTypeMapper.getInstance();
    for (LibraryPathType pathType: LibraryPathType.values()) {
      OrderRootType ideType = externalLibraryPathTypeMapper.map(pathType);
      if (ideType == null) continue;
      HashSet<String> toAddPerType = new HashSet<>(externalLibrary.getPaths(pathType));
      toAdd.put(ideType, toAddPerType);

      // do not remove attached or manually added sources/javadocs if nothing to add
      if(pathType != LibraryPathType.BINARY && toAddPerType.isEmpty()) {
        continue;
      }
      HashSet<String> toRemovePerType = new HashSet<>();
      toRemove.put(ideType, toRemovePerType);

      for (String url : ideLibrary.getUrls(ideType)) {
        String idePath = getLocalPath(url);
        if (!toAddPerType.remove(idePath)) {
          toRemovePerType.add(url);
        }
      }
    }
    if (toRemove.isEmpty() && toAdd.isEmpty()) {
      return;
    }

    final Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(ideLibrary);
    for (Map.Entry<OrderRootType, Set<String>> entry: toRemove.entrySet()) {
      for (String path: entry.getValue()) {
        libraryModel.removeRoot(path, entry.getKey());
      }
    }

    Set<String> excludedPaths = externalLibrary.getPaths(LibraryPathType.EXCLUDED);
    for (Map.Entry<OrderRootType, Set<String>> entry: toAdd.entrySet()) {
      Map<OrderRootType, Collection<File>> roots = new HashMap<>();
      roots.put(entry.getKey(), ContainerUtil.map(entry.getValue(), File::new));
      registerPaths(false, roots, excludedPaths, libraryModel, externalLibrary.getInternalName());
    }
  }

  @NotNull
  private static String getLocalPath(@NotNull String url) {
    if (url.startsWith(StandardFileSystems.JAR_PROTOCOL_PREFIX)) {
      url = StringUtil.trimEnd(url, JarFileSystem.JAR_SEPARATOR);
    }
    return VfsUtilCore.urlToPath(url);
  }
}
