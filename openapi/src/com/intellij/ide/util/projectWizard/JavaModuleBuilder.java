/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.projectWizard;


import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.ProjectJdk;
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
import org.jetbrains.annotations.Nullable;

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
  private ProjectJdk myJdk;

  public final String getContentEntryPath() {
    return myContentEntryPath;
  }

  public final void setContentEntryPath(String moduleRootPath) {
    final String path = acceptParameter(moduleRootPath);
    if (path != null) {
      try {
        // use canonical path to be sure symlinks are resolved
        myContentEntryPath = new File(path).getCanonicalPath();
      }
      catch (IOException e) {
        myContentEntryPath = path;
      }
    }
    else {
      myContentEntryPath = null;
    }
    if (myContentEntryPath != null) {
      myContentEntryPath = myContentEntryPath.replace(File.separatorChar, '/');
    }
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
    if (myJdk != null){
      rootModel.setJdk(myJdk);
    } else {
      rootModel.inheritJdk();
    }

    final String moduleRootPath = getContentEntryPath();
    if (moduleRootPath != null) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(moduleRootPath));
      if (moduleContentRoot != null) {
        final ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        final List<Pair<String,String>> sourcePaths = getSourcePaths();
        if (sourcePaths != null) {
          for (final Pair<String, String> sourcePath : sourcePaths) {
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
      rootModel.setCompilerOutputPath(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(canonicalPath)));
    }
    else {
      rootModel.inheritCompilerOutputPath(true);
    }

    LibraryTable libraryTable = rootModel.getModuleLibraryTable();
    for (Pair<String, String> libInfo : myModuleLibraries) {
      final String moduleLibraryPath = libInfo.first;
      final String sourceLibraryPath = libInfo.second;
      Library library = libraryTable.createLibrary();
      Library.ModifiableModel modifiableModel = library.getModifiableModel();
      modifiableModel.addRoot(getUrlByPath(moduleLibraryPath), OrderRootType.CLASSES);
      if (sourceLibraryPath != null) {
        modifiableModel.addRoot(getUrlByPath(sourceLibraryPath), OrderRootType.SOURCES);
      }
      modifiableModel.commit();
    }
  }

  private static String getUrlByPath(final String path) {
    return VfsUtil.getUrlForLibraryRoot(new File(path));
  }

  public void addModuleLibrary(String moduleLibraryPath, String sourcePath) {
    myModuleLibraries.add(Pair.create(moduleLibraryPath,sourcePath));
  }

  public void setModuleJdk(ProjectJdk jdk) {
    myJdk = jdk;
  }

  public ProjectJdk getModuleJdk() {
    return myJdk;
  }

  @Nullable
  protected String getPathForOutputPathStep() {
    return null;
  }
}
