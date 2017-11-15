package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 2/15/12 11:32 AM
 */
@Order(ExternalSystemConstants.BUILTIN_LIBRARY_DATA_SERVICE_ORDER)
public class LibraryDataService extends AbstractProjectDataService<LibraryData, Library> {

  private static final Logger LOG = Logger.getInstance(LibraryDataService.class);
  @NotNull public static final NotNullFunction<String, File> PATH_TO_FILE = path -> new File(path);

  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public LibraryDataService(@NotNull ExternalLibraryPathTypeMapper mapper) {
    myLibraryPathTypeMapper = mapper;
  }

  @NotNull
  @Override
  public Key<LibraryData> getTargetDataKey() {
    return ProjectKeys.LIBRARY;
  }

  @Override
  public void importData(@NotNull final Collection<DataNode<LibraryData>> toImport,
                         @Nullable final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    for (DataNode<LibraryData> dataNode : toImport) {
      importLibrary(dataNode.getData(), modelsProvider);
    }
  }

  private void importLibrary(@NotNull final LibraryData toImport, @NotNull final IdeModifiableModelsProvider modelsProvider) {
    Map<OrderRootType, Collection<File>> libraryFiles = prepareLibraryFiles(toImport);

    final String libraryName = toImport.getInternalName();
    Library library = modelsProvider.getLibraryByName(libraryName);
    if (library != null) {
      syncPaths(toImport, library, modelsProvider);
      return;
    }
    library = modelsProvider.createLibrary(libraryName, ExternalSystemApiUtil.toExternalSource(toImport.getOwner()));
    final Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    registerPaths(toImport.isUnresolved(), libraryFiles, libraryModel, libraryName);
  }

  @NotNull
  public Map<OrderRootType, Collection<File>> prepareLibraryFiles(@NotNull LibraryData data) {
    Map<OrderRootType, Collection<File>> result = ContainerUtilRt.newHashMap();
    for (LibraryPathType pathType : LibraryPathType.values()) {
      Set<String> paths = data.getPaths(pathType);
      if (paths.isEmpty()) {
        continue;
      }
      result.put(myLibraryPathTypeMapper.map(pathType), ContainerUtil.map(paths, PATH_TO_FILE));
    }
    return result;
  }

  static void registerPaths(boolean unresolved,
                            @NotNull Map<OrderRootType, Collection<File>> libraryFiles,
                            @NotNull Library.ModifiableModel model,
                            @NotNull String libraryName) {
    for (Map.Entry<OrderRootType, Collection<File>> entry : libraryFiles.entrySet()) {
      for (File file : entry.getValue()) {
        VirtualFile virtualFile = unresolved ? null : ExternalSystemUtil.refreshAndFindFileByIoFile(file);
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
  }

  /**
   * Remove orphan project libraries during postprocess phase (after execution of LibraryDependencyDataService#import)
   */
  @Override
  public void postProcess(@NotNull Collection<DataNode<LibraryData>> toImport,
                          @Nullable ProjectData projectData,
                          @NotNull Project project,
                          @NotNull IdeModifiableModelsProvider modelsProvider) {

    if (projectData == null) return;

    // do not cleanup orphan project libraries if import runs from Project Structure Dialog
    // since libraries order entries cannot be imported for modules in that case
    // and hence orphans will be detected incorrectly
    if (modelsProvider instanceof IdeUIModifiableModelsProvider) return;

    final List<Library> orphanIdeLibraries = ContainerUtil.newSmartList();
    final LibraryTable.ModifiableModel librariesModel = modelsProvider.getModifiableProjectLibrariesModel();
    final Map<String, Library> namesToLibs = ContainerUtil.newHashMap();
    final Set<Library> potentialOrphans = ContainerUtil.newHashSet();
    RootPolicy<Void> excludeUsedLibraries = new RootPolicy<Void>() {
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

    for (Library library : librariesModel.getLibraries()) {
      if (!ExternalSystemApiUtil.isExternalSystemLibrary(library, projectData.getOwner())) continue;
      namesToLibs.put(library.getName(), library);
      potentialOrphans.add(library);
    }

    for (Module module : modelsProvider.getModules()) {
      for (OrderEntry entry : modelsProvider.getOrderEntries(module)) {
        entry.accept(excludeUsedLibraries, null);
      }
    }

    for (Library lib : potentialOrphans) {
      if (!modelsProvider.isSubstituted(lib.getName())) {
        orphanIdeLibraries.add(lib);
      }
    }

    for (Library library : orphanIdeLibraries) {
      String libraryName = library.getName();
      if (libraryName != null) {
        Library libraryToRemove = librariesModel.getLibraryByName(libraryName);
        if (libraryToRemove != null) {
          librariesModel.removeLibrary(libraryToRemove);
        }
      }
    }
  }

  private void syncPaths(@NotNull final LibraryData externalLibrary,
                         @NotNull final Library ideLibrary,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    if (externalLibrary.isUnresolved()) {
      return;
    }
    final Map<OrderRootType, Set<String>> toRemove = ContainerUtilRt.newHashMap();
    final Map<OrderRootType, Set<String>> toAdd = ContainerUtilRt.newHashMap();
    for (LibraryPathType pathType : LibraryPathType.values()) {
      OrderRootType ideType = myLibraryPathTypeMapper.map(pathType);
      HashSet<String> toAddPerType = ContainerUtilRt.newHashSet(externalLibrary.getPaths(pathType));
      toAdd.put(ideType, toAddPerType);

      HashSet<String> toRemovePerType = ContainerUtilRt.newHashSet();
      toRemove.put(ideType, toRemovePerType);

      for (VirtualFile ideFile : ideLibrary.getFiles(ideType)) {
        String idePath = ExternalSystemApiUtil.getLocalFileSystemPath(ideFile);
        if (!toAddPerType.remove(idePath)) {
          toRemovePerType.add(ideFile.getUrl());
        }
      }
    }
    if (toRemove.isEmpty() && toAdd.isEmpty()) {
      return;
    }

    final Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(ideLibrary);
    for (Map.Entry<OrderRootType, Set<String>> entry : toRemove.entrySet()) {
      for (String path : entry.getValue()) {
        libraryModel.removeRoot(path, entry.getKey());
      }
    }

    for (Map.Entry<OrderRootType, Set<String>> entry : toAdd.entrySet()) {
      Map<OrderRootType, Collection<File>> roots = ContainerUtilRt.newHashMap();
      roots.put(entry.getKey(), ContainerUtil.map(entry.getValue(), PATH_TO_FILE));
      registerPaths(externalLibrary.isUnresolved(), roots, libraryModel, externalLibrary.getInternalName());
    }
  }
}
