/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.conversion.ConversionService;
import com.intellij.conversion.impl.ConversionServiceImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
@State(
  name = ProjectFileVersionImpl.COMPONENT_NAME,
  storages = {
    @Storage(
      id="other",
      file = "$PROJECT_FILE$"
    )
  }
)
public class ProjectFileVersionImpl extends ProjectFileVersion implements ProjectComponent, PersistentStateComponent<ProjectFileVersionImpl.ProjectFileVersionState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.convert.ProjectFileVersionImpl");
  @NonNls public static final String COMPONENT_NAME = "ProjectFileVersion";
  private Project myProject;

  public ProjectFileVersionImpl(Project project) {
    myProject = project;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    if (myProject.isDefault() || ApplicationManager.getApplication().isUnitTestMode()) return;
    final IProjectStore stateStore = ((ProjectEx)myProject).getStateStore();
    final String filePath;
    if (stateStore.getStorageScheme() == StorageScheme.DEFAULT) {
      filePath = stateStore.getProjectFilePath();
    }
    else {
      final VirtualFile baseDir = stateStore.getProjectBaseDir();
      filePath = baseDir != null ? baseDir.getPath() : null;
    }
    if (filePath != null) {
      ((ConversionServiceImpl)ConversionService.getInstance()).saveConversionResult(FileUtil.toSystemDependentName(filePath));
    }
    else {
      LOG.info("Cannot save conversion result: filePath == null");
    }
  }

  public ProjectFileVersionState getState() {
    return null;
  }

  public void loadState(final ProjectFileVersionState object) {
  }

  public static class ProjectFileVersionState {
  }
}
