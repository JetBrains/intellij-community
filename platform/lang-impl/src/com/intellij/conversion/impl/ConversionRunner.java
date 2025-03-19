// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion.impl;

import com.intellij.conversion.*;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class ConversionRunner {
  private final String providerId;
  private final ConverterProvider myProvider;
  private final ConversionContextImpl context;
  private final ConversionProcessor<ModuleSettings> myModuleFileConverter;
  private final ConversionProcessor<ComponentManagerSettings> myProjectFileConverter;
  private final ConversionProcessor<WorkspaceSettings> myWorkspaceConverter;
  private boolean myProcessProjectFile;
  private boolean myProcessWorkspaceFile;
  private boolean myProcessRunConfigurations;
  private boolean myProcessProjectLibraries;
  private boolean myArtifacts;
  private final List<Path> myModulesFilesToProcess = new ArrayList<>();
  private final ProjectConverter myConverter;
  private final ConversionProcessor<RunManagerSettings> myRunConfigurationsConverter;
  private final ConversionProcessor<ProjectLibrariesSettings> myProjectLibrariesConverter;
  private final ConversionProcessor<ArtifactsSettings> myArtifactsConverter;

  public ConversionRunner(@NotNull String providerId, @NotNull ConverterProvider provider, @NotNull ConversionContextImpl context) {
    this.providerId = providerId;
    myProvider = provider;
    this.context = context;
    myConverter = provider.createConverter(context);
    myModuleFileConverter = myConverter.createModuleFileConverter();
    myProjectFileConverter = myConverter.createProjectFileConverter();
    myWorkspaceConverter = myConverter.createWorkspaceFileConverter();
    myRunConfigurationsConverter = myConverter.createRunConfigurationsConverter();
    myProjectLibrariesConverter = myConverter.createProjectLibrariesConverter();
    myArtifactsConverter = myConverter.createArtifactsConverter();
  }

  public @NotNull String getProviderId() {
    return providerId;
  }

  public boolean isConversionNeeded() throws CannotConvertException {
    myProcessProjectFile = context.getStorageScheme() == StorageScheme.DEFAULT && myProjectFileConverter != null
                           && myProjectFileConverter.isConversionNeeded(context.getProjectSettings());

    myProcessWorkspaceFile = myWorkspaceConverter != null && Files.exists(context.getWorkspaceSettings().getPath())
                             && myWorkspaceConverter.isConversionNeeded(context.getWorkspaceSettings());

    myModulesFilesToProcess.clear();
    if (myModuleFileConverter != null) {
      for (Path moduleFile : context.getModulePaths()) {
        if (Files.exists(moduleFile) && myModuleFileConverter.isConversionNeeded(context.getModuleSettings(moduleFile))) {
          myModulesFilesToProcess.add(moduleFile);
        }
      }
    }

    myProcessRunConfigurations = myRunConfigurationsConverter != null
                                 && myRunConfigurationsConverter.isConversionNeeded(context.getRunManagerSettings$intellij_platform_lang_impl());

    myProcessProjectLibraries = myProjectLibrariesConverter != null
                                 && myProjectLibrariesConverter.isConversionNeeded(context.doGetProjectLibrarySettings$intellij_platform_lang_impl());

    myArtifacts = myArtifactsConverter != null
                  && myArtifactsConverter.isConversionNeeded(context.getArtifactSettings$intellij_platform_lang_impl());

    if (myProcessProjectFile ||
        myProcessWorkspaceFile ||
        myProcessRunConfigurations ||
        myProcessProjectLibraries ||
        !myModulesFilesToProcess.isEmpty()) {
      return true;
    }

    try {
      return myConverter.isConversionNeeded();
    }
    catch (Exception e) {
      Logger.getInstance(ConversionRunner.class).error("Converter of provider " + providerId + " cannot check is conversion needed or not", e);
      return false;
    }
  }

  public boolean isModuleConversionNeeded(@NotNull Path moduleFile) throws CannotConvertException {
    return myModuleFileConverter != null && myModuleFileConverter.isConversionNeeded(context.getModuleSettings(moduleFile));
  }

  public @NotNull Collection<Path> getCreatedFiles() {
    return myConverter.getCreatedFiles();
  }

  public void collectAffectedFiles(@NotNull Collection<? super Path> affectedFiles) {
    if (myProcessProjectFile) {
      affectedFiles.add(context.getProjectFile());
    }
    if (myProcessWorkspaceFile) {
      affectedFiles.add(context.getWorkspaceSettings().getPath());
    }
    affectedFiles.addAll(myModulesFilesToProcess);

    if (myProcessRunConfigurations) {
      try {
        context.getRunManagerSettings$intellij_platform_lang_impl().collectAffectedFiles(affectedFiles);
      }
      catch (CannotConvertException ignored) {
      }
    }
    if (myProcessProjectLibraries) {
      try {
        context.doGetProjectLibrarySettings$intellij_platform_lang_impl().collectAffectedFiles(affectedFiles);
      }
      catch (CannotConvertException ignored) {
      }
    }
    if (myArtifacts) {
      try {
        context.getArtifactSettings$intellij_platform_lang_impl().collectAffectedFiles(affectedFiles);
      }
      catch (CannotConvertException ignored) {
      }
    }

    affectedFiles.addAll(myConverter.getAdditionalAffectedFiles());
  }

  public void preProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.preProcess(context.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.preProcess(context.getWorkspaceSettings());
    }

    for (Path moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.preProcess(context.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.preProcess(context.getRunManagerSettings$intellij_platform_lang_impl());
    }

    if (myProcessProjectLibraries) {
      myProjectLibrariesConverter.preProcess(context.doGetProjectLibrarySettings$intellij_platform_lang_impl());
    }

    if (myArtifacts) {
      myArtifactsConverter.preProcess(context.getArtifactSettings$intellij_platform_lang_impl());
    }
    myConverter.preProcessingFinished();
  }

  public void process() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.process(context.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.process(context.getWorkspaceSettings());
    }

    for (Path moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.process(context.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.process(context.getRunManagerSettings$intellij_platform_lang_impl());
    }

    if (myProcessProjectLibraries) {
      myProjectLibrariesConverter.process(context.doGetProjectLibrarySettings$intellij_platform_lang_impl());
    }

    if (myArtifacts) {
      myArtifactsConverter.process(context.getArtifactSettings$intellij_platform_lang_impl());
    }
    myConverter.processingFinished();
  }

  public void postProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.postProcess(context.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.postProcess(context.getWorkspaceSettings());
    }

    for (Path moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.postProcess(context.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.postProcess(context.getRunManagerSettings$intellij_platform_lang_impl());
    }

    if (myProcessProjectLibraries) {
      myProjectLibrariesConverter.postProcess(context.doGetProjectLibrarySettings$intellij_platform_lang_impl());
    }

    if (myArtifacts) {
      myArtifactsConverter.postProcess(context.getArtifactSettings$intellij_platform_lang_impl());
    }
    myConverter.postProcessingFinished();
  }

  public @NotNull ConverterProvider getProvider() {
    return myProvider;
  }

  public static @NotNull List<Path> getReadOnlyFiles(@NotNull Collection<? extends Path> affectedFiles) {
    List<Path> result = new ArrayList<>();
    for (Path file : affectedFiles) {
      try {
        file.getFileSystem().provider().checkAccess(file, AccessMode.WRITE);
      }
      catch (NoSuchFileException ignored) {
      }
      catch (IOException ignored) {
        result.add(file);
      }
    }
    return result;
  }

  public void convertModule(@NotNull Path moduleFile) throws CannotConvertException {
    final ModuleSettings settings = context.getModuleSettings(moduleFile);
    myModuleFileConverter.preProcess(settings);
    myModuleFileConverter.process(settings);
    myModuleFileConverter.postProcess(settings);
  }
}
