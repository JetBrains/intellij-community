/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JavaModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {
  private String myCompilerOutputPath;
  // Pair<Source Path, Package Prefix>
  private List<Pair<String,String>> mySourcePaths;
  // Pair<Library path, Source path>
  private final List<Pair<String, String>> myModuleLibraries = new ArrayList<Pair<String, String>>();

  public final void setCompilerOutputPath(String compilerOutputPath) {
    myCompilerOutputPath = acceptParameter(compilerOutputPath);
  }

  public List<Pair<String,String>> getSourcePaths() {
    if (mySourcePaths == null) {
      final List<Pair<String, String>> paths = new ArrayList<Pair<String, String>>();
      @NonNls final String path = getContentEntryPath() + File.separator + "src";
      new File(path).mkdirs();
      paths.add(Pair.create(path, ""));
      return paths;
    }
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
    return StdModuleTypes.JAVA;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return sdkType instanceof JavaSdkType;
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return StdModuleTypes.JAVA.modifySettingsStep(settingsStep, this);
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return getModuleType().createWizardSteps(wizardContext, this, modulesProvider);
  }

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setExcludeOutput(true);
    if (myJdk != null){
      rootModel.setSdk(myJdk);
    } else {
      rootModel.inheritSdk();
    }

    ContentEntry contentEntry = doAddContentEntry(rootModel);
    if (contentEntry != null) {
      final List<Pair<String,String>> sourcePaths = getSourcePaths();

      if (sourcePaths != null) {
        for (final Pair<String, String> sourcePath : sourcePaths) {
          String first = sourcePath.first;
          new File(first).mkdirs();
          final VirtualFile sourceRoot = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(first));
          if (sourceRoot != null) {
            contentEntry.addSourceFolder(sourceRoot, false, sourcePath.second);
          }
        }
      }
    }

    if (myCompilerOutputPath != null) {
      // should set only absolute paths
      String canonicalPath;
      try {
        canonicalPath = FileUtil.resolveShortWindowsName(myCompilerOutputPath);
      }
      catch (IOException e) {
        canonicalPath = myCompilerOutputPath;
      }
      compilerModuleExtension
        .setCompilerOutputPath(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(canonicalPath)));
    }
    else {
      compilerModuleExtension.inheritCompilerOutputPath(true);
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

  @Nullable
  protected static String getPathForOutputPathStep() {
    return null;
  }
}
