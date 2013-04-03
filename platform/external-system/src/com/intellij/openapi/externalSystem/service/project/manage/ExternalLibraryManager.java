package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.Jar;
import com.intellij.openapi.externalSystem.model.project.ExternalLibrary;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;

import java.io.File;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 2/15/12 11:32 AM
 */
public class ExternalLibraryManager {

  private static final Logger LOG = Logger.getInstance("#" + ExternalLibraryManager.class.getName());

  @NotNull private final PlatformFacade                myPlatformFacade;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;
  @NotNull private final ExternalJarManager            myJarManager;

  public ExternalLibraryManager(@NotNull PlatformFacade platformFacade,
                                @NotNull ExternalLibraryPathTypeMapper mapper,
                                @NotNull ExternalJarManager manager)
  {
    myPlatformFacade = platformFacade;
    myLibraryPathTypeMapper = mapper;
    myJarManager = manager;
  }

  public void syncPaths(@NotNull ExternalLibrary gradleLibrary,
                        @NotNull final Library ideLibrary,
                        @NotNull Project project,
                        boolean synchronous)
  {
    Set<String> toRemove = ContainerUtilRt.newHashSet();
    Set<String> toAdd = ContainerUtilRt.newHashSet(gradleLibrary.getPaths(LibraryPathType.BINARY));
    for (VirtualFile ideFile : ideLibrary.getFiles(OrderRootType.CLASSES)) {
      String idePath = ExternalSystemUtil.getLocalFileSystemPath(ideFile);
      if (!toAdd.remove(idePath)) {
        toRemove.add(idePath);
      }
    }
    if (toRemove.isEmpty() && toAdd.isEmpty()) {
      return;
    }

    Function<String, Jar> jarMapper = new Function<String, Jar>() {
      @Override
      public Jar fun(String path) {
        return new Jar(path, LibraryPathType.BINARY, ideLibrary, null, ProjectSystemId.IDE);
      }
    };

    if (!toRemove.isEmpty()) {
      List<Jar> jarsToRemove = ContainerUtil.map(toRemove, jarMapper);
      myJarManager.removeJars(jarsToRemove, project, synchronous);
    }

    if (!toAdd.isEmpty()) {
      List<Jar> jarsToAdd = ContainerUtil.map(toAdd, jarMapper);
      myJarManager.importJars(jarsToAdd, project, synchronous);
    }
  }

  public void importLibraries(@NotNull Collection<? extends ExternalLibrary> libraries, @NotNull Project project, boolean synchronous) {
    for (ExternalLibrary library : libraries) {
      importLibrary(library, project, synchronous);
    }
  }

  public void importLibrary(@NotNull final ExternalLibrary library, @NotNull final Project project, boolean synchronous) {
    Map<OrderRootType, Collection<File>> libraryFiles = new HashMap<OrderRootType, Collection<File>>();
    for (LibraryPathType pathType : LibraryPathType.values()) {
      final Set<String> paths = library.getPaths(pathType);
      if (paths.isEmpty()) {
        continue;
      }
      libraryFiles.put(myLibraryPathTypeMapper.map(pathType), ContainerUtil.map(paths, new NotNullFunction<String, File>() {
        @NotNull
        @Override
        public File fun(String path) {
          return new File(path);
        }
      }));
    }
    importLibrary(library.getName(), libraryFiles, project, synchronous);
  }

  public void importLibrary(@NotNull final String libraryName,
                            @NotNull final Map<OrderRootType, ? extends Collection<File>> libraryFiles,
                            @NotNull final Project project,
                            boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(project, libraryName, synchronous, new Runnable() {
      @Override
      public void run() {
        // Is assumed to be called from the EDT.
        final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        final LibraryTable.ModifiableModel projectLibraryModel = libraryTable.getModifiableModel();
        final Library intellijLibrary;
        try {
          intellijLibrary = projectLibraryModel.createLibrary(libraryName);
        }
        finally {
          projectLibraryModel.commit();
        }
        final Library.ModifiableModel libraryModel = intellijLibrary.getModifiableModel();
        try {
          registerPaths(libraryFiles, libraryModel, libraryName);
        }
        finally {
          libraryModel.commit();
        }
      }
    });
  }

  private static void registerPaths(@NotNull final Map<OrderRootType, ? extends Collection<File>> libraryFiles,
                                    @NotNull Library.ModifiableModel model,
                                    @NotNull String libraryName)
  {
    for (Map.Entry<OrderRootType, ? extends Collection<File>> entry : libraryFiles.entrySet()) {
      Collection<File> value = entry.getValue();
      for (File file : value) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          if (entry.getKey() == OrderRootType.CLASSES) {
            LOG.warn(
              String.format("Can't find %s of the library '%s' at path '%s'", entry.getKey(), libraryName, file.getAbsolutePath())
            );
          }
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, entry.getKey());
        }
        else {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
          if (jarRoot == null) {
            LOG.warn(String.format(
              "Can't parse contents of the jar file at path '%s' for the library '%s''", file.getAbsolutePath(), libraryName
            ));
            continue;
          }
          model.addRoot(jarRoot, entry.getKey());
        }
      }
    }
  }

  public void removeLibraries(@NotNull final Collection<? extends Library> libraries, @NotNull final Project project) {
    if (libraries.isEmpty()) {
      return;
    }
    ExternalSystemUtil.executeProjectChangeAction(project, libraries, new Runnable() {
      @Override
      public void run() {
        final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        final LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
        try {
          for (Library library : libraries) {
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
}
