package com.intellij.ide.util.projectWizard;


import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JavaModuleBuilder extends ModuleBuilder {

  private String myContentEntryPath;
  private String myCompilerOutputPath;
  // Pair<Source Path, Package Prefix>
  private List<Pair<String,String>> mySourcePaths;
  // Pair<Library path, Source path>
  private List<Pair<String, String>> myModuleLibraries = new ArrayList<Pair<String, String>>();

  public final String getContentEntryPath() {
    return myContentEntryPath;
  }

  public final String getCompilerOutputPath() {
    return myCompilerOutputPath;
  }

  public final void setContentEntryPath(String moduleRootPath) {
    myContentEntryPath = acceptParameter(moduleRootPath);
  }

  public final void setCompilerOutputPath(String compilerOutputPath) {
    myCompilerOutputPath = acceptParameter(compilerOutputPath);
  }

  private List<Pair<String,String>> getSourcePaths() {
    return mySourcePaths;
  }

  public void setSourcePaths(List<Pair<String,String>> sourcePaths) {
    mySourcePaths = sourcePaths != null? new ArrayList<Pair<String, String>>(sourcePaths) : null;
  }

  public void addSourcePath(Pair<String,String> sourcePathInfo) {
    if (mySourcePaths == null) {
      mySourcePaths = new ArrayList<Pair<String, String>>();
    }
    mySourcePaths.add(sourcePathInfo);
  }

  public ModuleType getModuleType() {
    return ModuleType.JAVA;
  }

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    rootModel.setExcludeOutput(true);
    rootModel.inheritJdk();

    final String moduleRootPath = getContentEntryPath();
    if (moduleRootPath != null) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(moduleRootPath));
      if (moduleContentRoot != null) {
        final ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        final List<Pair<String,String>> sourcePaths = getSourcePaths();
        if (sourcePaths != null) {
          for (int idx = 0; idx < sourcePaths.size(); idx++) {
            final Pair<String,String> sourcePath = sourcePaths.get(idx);
            final VirtualFile sourceRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(sourcePath.first));
            if (sourceRoot != null) {
              contentEntry.addSourceFolder(sourceRoot, false, sourcePath.second);
            }
          }
        }
      }
    }

    if (myCompilerOutputPath != null) {
      // should set only absolute paths
      String canonicalPath;
      try {
        canonicalPath = new File(myCompilerOutputPath).getCanonicalPath();
      }
      catch (IOException e) {
        canonicalPath = myCompilerOutputPath;
      }
      rootModel.setCompilerOutputPath(VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, canonicalPath.replace(File.separatorChar, '/')));
    }
    else {
      rootModel.setCompilerOutputPath((VirtualFile)null);
    }

    LibraryTable libraryTable = rootModel.getModuleLibraryTable();
    for (int i = 0; i < myModuleLibraries.size(); i++) {
      Pair<String, String> libInfo = myModuleLibraries.get(i);
      final String moduleLibraryPath = libInfo.first;
      final String sourceLibraryPath = libInfo.second;
      Library library = libraryTable.createLibrary();
      Library.ModifiableModel modifiableModel = library.getModifiableModel();
      modifiableModel.addRoot(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(moduleLibraryPath)), OrderRootType.CLASSES);
      if (sourceLibraryPath != null) {
        modifiableModel.addRoot(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(sourceLibraryPath)), OrderRootType.SOURCES);
      }
      modifiableModel.commit();
    }
  }

  public void addModuleLibrary(String moduleLibraryPath, String sourcePath) {
    myModuleLibraries.add(Pair.create(moduleLibraryPath,sourcePath));
  }
}
