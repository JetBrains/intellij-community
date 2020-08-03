// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectLoadHelper;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

final class LightEditProjectImpl extends ProjectImpl implements LightEditCompatible {
  private static final Logger LOG = Logger.getInstance(LightEditProjectImpl.class);
  private static final String NAME = "LightEditProject";

  LightEditProjectImpl() {
    this(getProjectPath());
  }

  private LightEditProjectImpl(@NotNull Path projectPath) {
    super(projectPath, NAME);

    ProjectLoadHelper.registerComponents(this);
    customizeRegisteredComponents();
    getStateStore().setPath(projectPath, false, null);
    init(null);
  }

  private void customizeRegisteredComponents() {
    IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID);
    if (pluginDescriptor == null) {
      LOG.error("Could not find plugin by id: " + PluginManagerCore.CORE_ID);
      return;
    }
    registerService(DirectoryIndex.class, LightEditDirectoryIndex.class, pluginDescriptor, true);
    registerService(ProjectFileIndex.class, LightEditProjectFileIndex.class, pluginDescriptor, true);
    registerService(FileIndexFacade.class, LightEditFileIndexFacade.class, pluginDescriptor, true);
    registerService(DumbService.class, LightEditDumbService.class, pluginDescriptor, true);
    registerComponent(FileEditorManager.class, LightEditFileEditorManagerImpl.class, pluginDescriptor, true);
  }

  @NotNull
  private static Path getProjectPath() {
    return Paths.get(PathManager.getConfigPath() + File.separator + "light-edit");
  }

  @Override
  public void init(@Nullable ProgressIndicator indicator) {
    createComponents(null);
  }

  @Override
  public void setProjectName(@NotNull String name) {
    throw new IllegalStateException();
  }

  @Override
  @NotNull
  public String getName() {
    return NAME;
  }

  @Override
  @NotNull
  public String getLocationHash() {
    return getName();
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }
}
