package com.intellij.conversion.impl;

import com.intellij.conversion.*;
import com.intellij.openapi.components.StorageScheme;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ConversionRunner {
  private final ConverterProvider myProvider;
  private final ConversionContextImpl myContext;
  private final ConversionProcessor<ModuleSettings> myModuleFileConverter;
  private final ConversionProcessor<ProjectSettings> myProjectFileConverter;
  private final ConversionProcessor<WorkspaceSettings> myWorkspaceConverter;
  private boolean myProcessProjectFile;
  private boolean myProcessWorkspaceFile;
  private List<File> myModulesFilesToProcess = new ArrayList<File>();
  private ProjectConverter myConverter;

  public ConversionRunner(ConverterProvider provider, ConversionContextImpl context) {
    myProvider = provider;
    myContext = context;
    myConverter = provider.createConverter(context);
    myModuleFileConverter = myConverter.createModuleFileConverter();
    myProjectFileConverter = myConverter.createProjectFileConverter();
    myWorkspaceConverter = myConverter.createWorkspaceFileConverter();
  }

  public boolean isConversionNeeded() throws CannotConvertException {
    myProcessProjectFile = myContext.getStorageScheme() == StorageScheme.DEFAULT && myProjectFileConverter != null
                           && myProjectFileConverter.isConversionNeeded(myContext.getProjectSettings());

    myProcessWorkspaceFile = myWorkspaceConverter != null && myContext.getWorkspaceFile().exists()
                             && myWorkspaceConverter.isConversionNeeded(myContext.getWorkspaceSettings());

    if (myModuleFileConverter != null) {
      for (File moduleFile : myContext.getModuleFiles()) {
        if (moduleFile.exists() && myModuleFileConverter.isConversionNeeded(myContext.getModuleSettings(moduleFile))) {
          myModulesFilesToProcess.add(moduleFile);
        }
      }
    }
    return myProcessProjectFile || myProcessWorkspaceFile || !myModulesFilesToProcess.isEmpty();
  }

  public boolean isModuleConversionNeeded(File moduleFile) throws CannotConvertException {
    if (myModuleFileConverter != null && myModuleFileConverter.isConversionNeeded(myContext.getModuleSettings(moduleFile))) {
      return true;
    }
    return false;
  }

  public void preProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.preProcess(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.preProcess(myContext.getWorkspaceSettings());
    }

    for (File moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.preProcess(myContext.getModuleSettings(moduleFile));
    }
  }

  public List<File> getAffectedFiles() {
    List<File> affectedFiles = new ArrayList<File>();
    if (myProcessProjectFile) {
      affectedFiles.add(myContext.getProjectFile());
    }
    if (myProcessWorkspaceFile) {
      affectedFiles.add(myContext.getWorkspaceFile());
    }
    affectedFiles.addAll(myModulesFilesToProcess);
    return affectedFiles;
  }

  public void process() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.process(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.process(myContext.getWorkspaceSettings());
    }

    for (File moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.process(myContext.getModuleSettings(moduleFile));
    }
  }

  public void postProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.postProcess(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.postProcess(myContext.getWorkspaceSettings());
    }

    for (File moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.postProcess(myContext.getModuleSettings(moduleFile));
    }
    myConverter.postProcess();
  }

  public ConverterProvider getProvider() {
    return myProvider;
  }

  public static List<File> getReadOnlyFiles(final Collection<File> affectedFiles) {
    List<File> result = new ArrayList<File>();
    for (File file : affectedFiles) {
      if (!file.canWrite()) {
        result.add(file);
      }
    }
    return result;
  }

  public void convertModule(File moduleFile) throws CannotConvertException {
    final ModuleSettings settings = myContext.getModuleSettings(moduleFile);
    myModuleFileConverter.preProcess(settings);
    myModuleFileConverter.process(settings);
    myModuleFileConverter.postProcess(settings);
  }
}
