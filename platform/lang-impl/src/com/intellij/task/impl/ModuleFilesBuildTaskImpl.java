// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.ModuleFilesBuildTask;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public final class ModuleFilesBuildTaskImpl extends ModuleBuildTaskImpl implements ModuleFilesBuildTask {
  private final VirtualFile[] myFiles;

  public ModuleFilesBuildTaskImpl(Module module, boolean isIncrementalBuild, VirtualFile... files) {
    super(module, isIncrementalBuild);
    myFiles = files;
  }

  public ModuleFilesBuildTaskImpl(Module module, boolean isIncrementalBuild, Collection<? extends VirtualFile> files) {
    this(module, isIncrementalBuild, files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  @Override
  public VirtualFile[] getFiles() {
    return myFiles;
  }

  @Override
  public @NotNull String getPresentableName() {
    return LangBundle.message("project.task.name.files.build.task.0", Arrays.toString(myFiles));
  }
}
