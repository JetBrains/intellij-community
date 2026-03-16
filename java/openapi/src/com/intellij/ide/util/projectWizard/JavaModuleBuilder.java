// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaRelease;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;

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
      String contentEntry = Objects.requireNonNull(getContentEntryPath());
      final @NonNls Path path = Path.of(contentEntry).resolve("src");
      try {
        NioFiles.createDirectories(path);
      }
      catch (IOException e) {
        LOG.error(e);
        new File(path.toString()).mkdirs(); // maybe this will succeed...
      }
      paths.add(Pair.create(path.toString(), ""));
      return paths;
    }
    return mySourcePaths;
  }

  @Override
  public boolean isAvailable() {
    return false;
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
  public ModuleType<?> getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return sdkType instanceof JavaSdkType && !((JavaSdkType)sdkType).isDependent();
  }

  @Override
  public @Nullable ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
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

  @Override
  public @Nullable List<Module> commit(@NotNull Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
    ApplicationManager.getApplication().runWriteAction(() -> setProjectLanguageLevel(project));
    return super.commit(project, model, modulesProvider);
  }

  private static void setProjectLanguageLevel(@NotNull Project project) {
    LanguageLevel defaultLanguageLevel = getDefaultLanguageLevel();
    LanguageLevelProjectExtension instance = LanguageLevelProjectExtension.getInstance(project);
    if (defaultLanguageLevel != null) {
      instance.setLanguageLevel(defaultLanguageLevel);
    }
    else {
      instance.setDefault(true);
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      LOG.debug("commit: projectSdk=" + sdk);
      if (sdk != null) {
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
        LOG.debug("commit: sdk.version=" + version);
      }
    }
  }

  private static @Nullable LanguageLevel getDefaultLanguageLevel() {
    // this is a fallback to simulate old behavior. Please delete this code (always return null). Wizard should have its own
    // "default sdk" setting if needed instead of relying on registry option or default project: project sdk is stored in the WSM now,
    // but default projects do not have workspace model (at least for now).
    try {
      String level = Registry.stringValue("default.language.level.name");

      if (level.isBlank()) {
        return null;
      }
      else {
        for (LanguageLevel languageLevel : LanguageLevel.getEntries()) {
          if (level.equals(languageLevel.name())) {
            return languageLevel;
          }
        }
        return JavaRelease.getHighest();
      }
    }
    catch (MissingResourceException ignored) {
      return null;
    }
  }

  private static String getUrlByPath(final String path) {
    return VfsUtil.getUrlForLibraryRoot(new File(path));
  }

  public void addModuleLibrary(String moduleLibraryPath, String sourcePath) {
    myModuleLibraries.add(Pair.create(moduleLibraryPath,sourcePath));
  }

  protected static @Nullable String getPathForOutputPathStep() {
    return null;
  }

  @Override
  public int getWeight() {
    return JAVA_WEIGHT;
  }
}
