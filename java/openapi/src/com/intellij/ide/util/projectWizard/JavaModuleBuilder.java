// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;


import com.intellij.ide.NewProjectWizardLegacy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JavaModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {
  private static final Logger LOG = Logger.getInstance(JavaModuleBuilder.class);
  private String myCompilerOutputPath;
  // Pair<Source Path, Package Prefix>
  private List<Pair<String,String>> mySourcePaths;
  // Pair<Library path, Source path>
  private final List<Pair<String, String>> myModuleLibraries = new ArrayList<>();
  public static final int BUILD_SYSTEM_WEIGHT = JVM_WEIGHT;
  public static final int JAVA_WEIGHT = BUILD_SYSTEM_WEIGHT + 20;
  public static final int JAVA_MOBILE_WEIGHT = 60;

  public final void setCompilerOutputPath(String compilerOutputPath) {
    myCompilerOutputPath = acceptParameter(compilerOutputPath);
  }

  @Override
  public List<Pair<String,String>> getSourcePaths() {
    if (mySourcePaths == null) {
      final List<Pair<String, String>> paths = new ArrayList<>();
      @NonNls final String path = getContentEntryPath() + File.separator + "src";
      new File(path).mkdirs();
      paths.add(Pair.create(path, ""));
      return paths;
    }
    return mySourcePaths;
  }

  @Override
  public boolean isAvailable() {
    return NewProjectWizardLegacy.isAvailable();
  }

  @Override
  public void setSourcePaths(List<Pair<String,String>> sourcePaths) {
    mySourcePaths = sourcePaths != null ? new ArrayList<>(sourcePaths) : null;
  }

  @Override
  public void addSourcePath(Pair<String,String> sourcePathInfo) {
    if (mySourcePaths == null) {
      mySourcePaths = new ArrayList<>();
    }
    mySourcePaths.add(sourcePathInfo);
  }

  @Override
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return sdkType instanceof JavaSdkType && !((JavaSdkType)sdkType).isDependent();
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return StdModuleTypes.JAVA.modifySettingsStep(settingsStep, this);
  }

  @Override
  public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
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
        .setCompilerOutputPath(VfsUtilCore.pathToUrl(canonicalPath));
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

  @Nullable
  @Override
  public List<Module> commit(@NotNull Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject());
    Boolean aDefault = extension.getDefault();
    LOG.debug("commit: aDefault=" + aDefault);
    LanguageLevelProjectExtension instance = LanguageLevelProjectExtension.getInstance(project);
    if (aDefault != null && !aDefault) {
      instance.setLanguageLevel(extension.getLanguageLevel());
    }
    else {
      //setup language level according to jdk, then setup default flag
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      LOG.debug("commit: projectSdk=" + sdk);
      if (sdk != null) {
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
        LOG.debug("commit: sdk.version=" + version);
        if (version != null) {
          instance.setLanguageLevel(version.getMaxLanguageLevel());
          instance.setDefault(true);
        }
      }
    }
    return super.commit(project, model, modulesProvider);
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

  @Override
  public int getWeight() {
    return JAVA_WEIGHT;
  }
}
