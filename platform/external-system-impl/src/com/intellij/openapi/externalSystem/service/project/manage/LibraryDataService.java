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
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/15/12 11:32 AM
 */
@Order(ExternalSystemConstants.BUILTIN_LIBRARY_DATA_SERVICE_ORDER)
public class LibraryDataService extends AbstractProjectDataService<LibraryData, Library> {

  private static final Logger LOG = Logger.getInstance("#" + LibraryDataService.class.getName());
  @NotNull public static final NotNullFunction<String, File> PATH_TO_FILE = new NotNullFunction<String, File>() {
    @NotNull
    @Override
    public File fun(String path) {
      return new File(path);
    }
  };

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
                         @Nullable ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final PlatformFacade platformFacade,
                         final boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        for (DataNode<LibraryData> dataNode : toImport) {
          importLibrary(dataNode.getData(), project, platformFacade, synchronous);
        }
      }
    });
  }

  private void importLibrary(@NotNull LibraryData toImport,
                             @NotNull Project project,
                             @NotNull PlatformFacade platformFacade,
                             boolean synchronous) {
    Map<OrderRootType, Collection<File>> libraryFiles = prepareLibraryFiles(toImport);

    Library library = platformFacade.findIdeLibrary(toImport, project);
    if (library != null) {
      syncPaths(toImport, library, project, synchronous);
      return;
    }
    importLibrary(toImport.getInternalName(), libraryFiles, project, platformFacade);
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

  private void importLibrary(@NotNull String libraryName,
                             @NotNull Map<OrderRootType, Collection<File>> libraryFiles,
                             @NotNull Project project,
                             @NotNull PlatformFacade platformFacade)
  {
    // Is assumed to be called from the EDT.
    LibraryTable libraryTable = platformFacade.getProjectLibraryTable(project);
    LibraryTable.ModifiableModel projectLibraryModel = libraryTable.getModifiableModel();
    Library intellijLibrary;
    try {
      intellijLibrary = projectLibraryModel.createLibrary(libraryName);
    }
    finally {
      projectLibraryModel.commit();
    }
    Library.ModifiableModel libraryModel = intellijLibrary.getModifiableModel();
    try {
      registerPaths(libraryFiles, libraryModel, libraryName);
    }
    finally {
      libraryModel.commit();
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void registerPaths(@NotNull Map<OrderRootType, Collection<File>> libraryFiles,
                            @NotNull Library.ModifiableModel model,
                            @NotNull String libraryName)
  {
    for (Map.Entry<OrderRootType, Collection<File>> entry : libraryFiles.entrySet()) {
      for (File file : entry.getValue()) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          if (ExternalSystemConstants.VERBOSE_PROCESSING && entry.getKey() == OrderRootType.CLASSES) {
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

  @Override
  public void removeData(@NotNull final Computable<Collection<Library>> toRemoveComputable,
                         @NotNull Collection<DataNode<LibraryData>> toIgnore,
                         @NotNull ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final PlatformFacade platformFacade,
                         boolean synchronous) {
    final Collection<Library> toRemove = toRemoveComputable.compute();
    if (toRemove.isEmpty()) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        final LibraryTable libraryTable = platformFacade.getProjectLibraryTable(project);
        final LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
        try {
          for (Library library : toRemove) {
            String libraryName = library.getName();
            if (libraryName != null) {
              Library libraryToRemove = model.getLibraryByName(libraryName);
              if (libraryToRemove != null) {
                model.removeLibrary(libraryToRemove);
              }
            }
          }
        }
        finally {
          model.commit();
        }
      }
    });
  }

  public void syncPaths(@NotNull LibraryData externalLibrary, @NotNull Library ideLibrary, @NotNull Project project, boolean synchronous) {
    if (externalLibrary.isUnresolved()) {
      return;
    }
    Map<OrderRootType, Set<String>> toRemove = ContainerUtilRt.newHashMap();
    Map<OrderRootType, Set<String>> toAdd = ContainerUtilRt.newHashMap();
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
    Library.ModifiableModel model = ideLibrary.getModifiableModel();
    try {
      for (Map.Entry<OrderRootType, Set<String>> entry : toRemove.entrySet()) {
        for (String path : entry.getValue()) {
          model.removeRoot(path, entry.getKey());
        }
      }

      for (Map.Entry<OrderRootType, Set<String>> entry : toAdd.entrySet()) {
        Map<OrderRootType, Collection<File>> roots = ContainerUtilRt.newHashMap();
        roots.put(entry.getKey(), ContainerUtil.map(entry.getValue(), PATH_TO_FILE));
        registerPaths(roots, model, externalLibrary.getInternalName());
      }
    }
    finally {
      model.commit();
    }
  }
}
