// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.*;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ConversionRunner {
  private final ConverterProvider myProvider;
  private final ConversionContextImpl myContext;
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

  public ConversionRunner(@NotNull ConverterProvider provider, @NotNull ConversionContextImpl context) {
    myProvider = provider;
    myContext = context;
    myConverter = provider.createConverter(context);
    myModuleFileConverter = myConverter.createModuleFileConverter();
    myProjectFileConverter = myConverter.createProjectFileConverter();
    myWorkspaceConverter = myConverter.createWorkspaceFileConverter();
    myRunConfigurationsConverter = myConverter.createRunConfigurationsConverter();
    myProjectLibrariesConverter = myConverter.createProjectLibrariesConverter();
    myArtifactsConverter = myConverter.createArtifactsConverter();
  }

  public boolean isConversionNeeded() throws CannotConvertException {
    myProcessProjectFile = myContext.getStorageScheme() == StorageScheme.DEFAULT && myProjectFileConverter != null
                           && myProjectFileConverter.isConversionNeeded(myContext.getProjectSettings());

    myProcessWorkspaceFile = myWorkspaceConverter != null && Files.exists(myContext.getWorkspaceSettings().getPath())
                             && myWorkspaceConverter.isConversionNeeded(myContext.getWorkspaceSettings());

    myModulesFilesToProcess.clear();
    if (myModuleFileConverter != null) {
      for (Path moduleFile : myContext.getModulePaths()) {
        if (Files.exists(moduleFile) && myModuleFileConverter.isConversionNeeded(myContext.getModuleSettings(moduleFile))) {
          myModulesFilesToProcess.add(moduleFile);
        }
      }
    }

    myProcessRunConfigurations = myRunConfigurationsConverter != null
                                 && myRunConfigurationsConverter.isConversionNeeded(myContext.getRunManagerSettings());

    myProcessProjectLibraries = myProjectLibrariesConverter != null
                                 && myProjectLibrariesConverter.isConversionNeeded(myContext.getProjectLibrariesSettings());

    myArtifacts = myArtifactsConverter != null
                  && myArtifactsConverter.isConversionNeeded(myContext.getArtifactsSettings());

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
      Logger.getInstance(ConversionRunner.class).error("Converter of provider " + myProvider.getId() + " cannot check is conversion needed or not", e);
      return false;
    }
  }

  public boolean isModuleConversionNeeded(@NotNull Path moduleFile) throws CannotConvertException {
    return myModuleFileConverter != null && myModuleFileConverter.isConversionNeeded(myContext.getModuleSettings(moduleFile));
  }

  public @NotNull Collection<Path> getCreatedFiles() {
    return myConverter.getCreatedFiles();
  }

  public void collectAffectedFiles(@NotNull Collection<Path> affectedFiles) {
    if (myProcessProjectFile) {
      affectedFiles.add(myContext.getProjectFile());
    }
    if (myProcessWorkspaceFile) {
      affectedFiles.add(myContext.getWorkspaceSettings().getPath());
    }
    affectedFiles.addAll(myModulesFilesToProcess);

    if (myProcessRunConfigurations) {
      try {
        myContext.getRunManagerSettings().collectAffectedFiles(affectedFiles);
      }
      catch (CannotConvertException ignored) {
      }
    }
    if (myProcessProjectLibraries) {
      try {
        myContext.getProjectLibrariesSettings().collectAffectedFiles(affectedFiles);
      }
      catch (CannotConvertException ignored) {
      }
    }
    if (myArtifacts) {
      try {
        myContext.getArtifactsSettings().collectAffectedFiles(affectedFiles);
      }
      catch (CannotConvertException ignored) {
      }
    }

    affectedFiles.addAll(myConverter.getAdditionalAffectedFiles());
  }

  public void preProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.preProcess(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.preProcess(myContext.getWorkspaceSettings());
    }

    for (Path moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.preProcess(myContext.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.preProcess(myContext.getRunManagerSettings());
    }

    if (myProcessProjectLibraries) {
      myProjectLibrariesConverter.preProcess(myContext.getProjectLibrariesSettings());
    }

    if (myArtifacts) {
      myArtifactsConverter.preProcess(myContext.getArtifactsSettings());
    }
    myConverter.preProcessingFinished();
  }

  public void process() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.process(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.process(myContext.getWorkspaceSettings());
    }

    for (Path moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.process(myContext.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.process(myContext.getRunManagerSettings());
    }

    if (myProcessProjectLibraries) {
      myProjectLibrariesConverter.process(myContext.getProjectLibrariesSettings());
    }

    if (myArtifacts) {
      myArtifactsConverter.process(myContext.getArtifactsSettings());
    }
    myConverter.processingFinished();
  }

  public void postProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.postProcess(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.postProcess(myContext.getWorkspaceSettings());
    }

    for (Path moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.postProcess(myContext.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.postProcess(myContext.getRunManagerSettings());
    }

    if (myProcessProjectLibraries) {
      myProjectLibrariesConverter.postProcess(myContext.getProjectLibrariesSettings());
    }

    if (myArtifacts) {
      myArtifactsConverter.postProcess(myContext.getArtifactsSettings());
    }
    myConverter.postProcessingFinished();
  }

  public ConverterProvider getProvider() {
    return myProvider;
  }

  public static @NotNull List<Path> getReadOnlyFiles(@NotNull Collection<Path> affectedFiles) {
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
    final ModuleSettings settings = myContext.getModuleSettings(moduleFile);
    myModuleFileConverter.preProcess(settings);
    myModuleFileConverter.process(settings);
    myModuleFileConverter.postProcess(settings);
  }
}
